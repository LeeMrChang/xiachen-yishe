"""Exercise the CNB release bridge without network access or real credentials."""
import base64
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

spec = importlib.util.spec_from_file_location("cnb_release", Path(__file__).parents[1] / "scripts/cnb-release.py")
cnb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cnb)


class CNBReleaseTest(unittest.TestCase):
    def test_registry_scope_and_no_password_in_trigger(self):
        headers = {"WWW-Authenticate": 'Bearer realm="https://ccr.ccs.tencentyun.com/service/token",service="token-service"'}
        challenge = urllib.error.HTTPError("url", 401, "private", headers, None)
        with patch.object(cnb.HTTP, "open", side_effect=challenge), patch.object(cnb, "request", return_value=(b'{"token":"short-lived","expires_in":1800}', {})) as req:
            self.assertEqual(cnb.registry_token("namespace/xc-api", "user", "private-password"), "short-lived")
            self.assertIn("scope=repository%3Anamespace%2Fxc-api%3Apull%2Cpush", req.call_args.args[0])
            self.assertNotIn("private-password", req.call_args.args[0])

    def test_rejects_foreign_credential_destination(self):
        headers = {"WWW-Authenticate": 'Bearer realm="https://example.com/steal"'}
        challenge = urllib.error.HTTPError("url", 401, "private", headers, None)
        with patch.object(cnb.HTTP, "open", side_effect=challenge), patch.object(cnb, "request") as req:
            with self.assertRaisesRegex(RuntimeError, "Untrusted"):
                cnb.registry_token("namespace/xc-api", "user", "private-password")
            req.assert_not_called()

    def release(self, state):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        output = Path(temp.name) / "output"
        env = {"MODULE": "xc-api", "GITHUB_SHA": "a" * 40, "CNB_TOKEN": "cnb-private",
               "TCR_USERNAME": "user", "TCR_PASSWORD": "private-password", "GITHUB_OUTPUT": str(output)}
        manifest = b'{"schemaVersion":2}'
        calls = []
        def remote(url, **kwargs):
            calls.append((url, kwargs))
            if url.endswith("/start"):
                auth = json.loads(base64.b64decode(kwargs["data"]["env"]["DOCKER_AUTH_B64"]))
                self.assertEqual(auth["auths"][cnb.REGISTRY], {"registrytoken": "temporary-upload"})
                self.assertNotIn("private-password", json.dumps(kwargs))
                return b'{"sn":"build-1","success":true}', {}
            if "/status/" in url:
                return json.dumps({"status": state}).encode(), {}
            if "/stop/" in url:
                return b'{}', {}
            return manifest, {"Docker-Content-Digest": "sha256:" + hashlib.sha256(manifest).hexdigest()}
        with patch.dict(os.environ, env), patch.object(cnb, "mirror"), patch.object(cnb, "registry_token", return_value="temporary-upload"), patch.object(cnb, "request", side_effect=remote), patch("sys.stdout", new_callable=io.StringIO), patch.object(cnb.signal, "signal"):
            if state == "success":
                cnb.main()
                self.assertIn("@sha256:", output.read_text())
            else:
                with self.assertRaisesRegex(RuntimeError, "deployment skipped"):
                    cnb.main()
                self.assertFalse(output.exists())
                self.assertTrue(any("/stop/" in url for url, _ in calls))

    def test_success_returns_immutable_digest(self):
        self.release("success")

    def test_failed_build_cannot_deploy(self):
        self.release("failed")


if __name__ == "__main__":
    unittest.main()
