#!/usr/bin/env python3
"""Mirror a checked commit, publish on CNB, and return a TCR digest.

Long-lived passwords stay in GitHub. CNB receives only a repository-scoped
registry bearer token. Never print response bodies containing credentials.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

CNB_REPO = "leemrchang-ci/xiachen-builder"
REGISTRY = "ccr.ccs.tencentyun.com"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("Refusing to redirect authenticated release request")


HTTP = urllib.request.build_opener(NoRedirect)


class HTTPFailure(RuntimeError):
    def __init__(self, host, status):
        self.status = status
        super().__init__(f"{host}: HTTP {status}")


def request(url, *, token=None, data=None, headers=None):
    hdr = dict(headers or {})
    if token:
        hdr["Authorization"] = "Bearer " + token
    if data is not None:
        hdr["Content-Type"] = "application/json"
        data = json.dumps(data).encode()
    req = urllib.request.Request(url, data=data, headers=hdr)
    try:
        with HTTP.open(req, timeout=45) as response:
            return response.read(), response.headers
    except urllib.error.HTTPError as exc:
        # The remote body can echo build variables, so deliberately omit it.
        raise HTTPFailure(urllib.parse.urlparse(url).hostname, exc.code) from None


def manifest_digest(repository, sha, token):
    accept = ", ".join([
        "application/vnd.docker.distribution.manifest.v2+json",
        "application/vnd.docker.distribution.manifest.list.v2+json",
        "application/vnd.oci.image.manifest.v1+json",
        "application/vnd.oci.image.index.v1+json",
    ])
    for attempt in range(12):
        try:
            raw, headers = request(f"https://{REGISTRY}/v2/{repository}/manifests/{sha}",
                                   token=token, headers={"Accept": accept})
            break
        except HTTPFailure as exc:
            if exc.status != 404 or attempt == 11:
                raise
            time.sleep(5)
    digest = "sha256:" + hashlib.sha256(raw).hexdigest()
    if headers.get("Docker-Content-Digest") != digest:
        raise RuntimeError("TCR manifest digest mismatch")
    return digest


def registry_token(repository, username, password):
    url = f"https://{REGISTRY}/v2/{repository}/tags/list"
    try:
        HTTP.open(url, timeout=30).close()
        raise RuntimeError("Expected private TCR repository authentication challenge")
    except urllib.error.HTTPError as exc:
        if exc.code != 401:
            raise RuntimeError(f"TCR authentication challenge: HTTP {exc.code}") from None
        challenge = exc.headers.get("WWW-Authenticate", "")
    fields = dict(re.findall(r'(\w+)="([^"\r\n]+)"', challenge))
    realm = urllib.parse.urlparse(fields.get("realm", ""))
    if realm.scheme != "https" or realm.hostname != REGISTRY or realm.username or realm.password:
        raise RuntimeError("Untrusted registry authentication realm")
    query = urllib.parse.parse_qsl(realm.query) + [
        ("service", fields.get("service", REGISTRY)),
        ("scope", f"repository:{repository}:pull,push"),
    ]
    endpoint = urllib.parse.urlunparse(realm._replace(query=urllib.parse.urlencode(query)))
    auth = base64.b64encode(f"{username}:{password}".encode()).decode()
    raw, _ = request(endpoint, headers={"Authorization": "Basic " + auth})
    result = json.loads(raw)
    token = result.get("token") or result.get("access_token")
    if not token:
        raise RuntimeError("TCR did not issue a scoped registry token")
    if int(result.get("expires_in", 0)) < 600:
        raise RuntimeError("TCR upload token lifetime is too short for a build")
    return token


def mirror(sha, token):
    with tempfile.TemporaryDirectory(prefix="cnb-auth-") as directory:
        helper = Path(directory) / "askpass"
        helper.write_text('#!/bin/sh\ncase "$1" in *Username*) printf "%s" cnb;; *) printf "%s" "$CNB_TOKEN";; esac\n')
        helper.chmod(0o700)
        env = dict(os.environ, CNB_TOKEN=token, GIT_ASKPASS=str(helper), GIT_TERMINAL_PROMPT="0")
        subprocess.run([
            "git", "-c", "credential.helper=", "push",
            f"https://cnb.cool/{CNB_REPO}.git", f"{sha}:refs/heads/main",
        ], env=env, check=True)


def main():
    module = os.environ["MODULE"]
    sha = os.environ["GITHUB_SHA"]
    if module not in ("xc-api", "xc-admin") or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise RuntimeError("Invalid release module or commit")
    image = f"{REGISTRY}/leemrchang-xiachen-yishe/{module}"
    repository = image.split("/", 1)[1]
    cnb_token = os.environ["CNB_TOKEN"]
    mirror(sha, cnb_token)
    upload_token = registry_token(repository, os.environ["TCR_USERNAME"], os.environ["TCR_PASSWORD"])
    auth = {"auths": {REGISTRY: {"registrytoken": upload_token}}}
    api = f"https://api.cnb.cool/{CNB_REPO}/-/build"
    raw, _ = request(api + "/start", token=cnb_token, data={
        "branch": "main", "sha": sha, "event": "api_trigger",
        "title": f"{module} {sha[:12]}",
        "env": {"MODULE": module, "SOURCE_SHA": sha, "IMAGE": image,
                "DOCKER_AUTH_B64": base64.b64encode(json.dumps(auth).encode()).decode()},
    })
    result = json.loads(raw)
    sn = result.get("sn", "")
    if not result.get("success") or not re.fullmatch(r"[A-Za-z0-9_-]+", sn):
        raise RuntimeError("CNB did not accept the build")
    print(f"CNB build: https://cnb.cool/{CNB_REPO}/-/build/logs/{sn}", flush=True)
    def interrupted(signum, frame):
        raise KeyboardInterrupt("GitHub job interrupted")
    signal.signal(signal.SIGTERM, interrupted)
    deadline = time.monotonic() + 2400
    try:
        while time.monotonic() < deadline:
            raw, _ = request(api + "/status/" + sn, token=cnb_token)
            status = str(json.loads(raw).get("status", "")).lower()
            print(f"CNB status: {status}", flush=True)
            if status in ("success", "succeeded", "passed"):
                break
            if status in ("fail", "failed", "failure", "canceled", "cancelled", "error", "timeout"):
                raise RuntimeError(f"CNB build {status}; deployment skipped")
            time.sleep(15)
        else:
            raise RuntimeError("CNB build exceeded 40 minutes")
    except BaseException:
        try:
            request(api + "/stop/" + sn, token=cnb_token, data={})
        except Exception:
            pass
        raise
    # Refresh the token before reading the immutable result.
    read_token = registry_token(repository, os.environ["TCR_USERNAME"], os.environ["TCR_PASSWORD"])
    digest = manifest_digest(repository, sha, read_token)
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"image_ref={image}@{digest}\n")
    print(f"Published {image}@{digest}")


if __name__ == "__main__":
    main()
