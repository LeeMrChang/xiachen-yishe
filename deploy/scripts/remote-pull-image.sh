#!/usr/bin/env bash
# Use job-scoped credentials; no compilation or persistent registry login on the host.
set -euo pipefail
: "${DEPLOY_PATH:?}" "${SERVICE:?}" "${TAG:?}" "${IMAGE_REF:?}" "${REGISTRY_USER:?}" "${REGISTRY_PASSWORD:?}"
case "$SERVICE" in xc-api|xc-admin) ;; *) exit 1;; esac
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]]
[[ "$IMAGE_REF" =~ ^ccr\.ccs\.tencentyun\.com/[a-z0-9][a-z0-9._-]*/$SERVICE@sha256:[0-9a-f]{64}$ ]] || {
  echo 'Expected an immutable TCR image reference' >&2; exit 1;
}
cd "$DEPLOY_PATH"
exec 9>.deploy.lock
flock -w 60 9
registry_config=$(mktemp -d)
trap 'rm -rf "$registry_config"' EXIT
trap 'exit 130' HUP INT TERM
export DOCKER_CONFIG="$registry_config"
printf '%s' "$REGISTRY_PASSWORD" | timeout 30 docker login ccr.ccs.tencentyun.com --username "$REGISTRY_USER" --password-stdin
unset REGISTRY_PASSWORD
started=$SECONDS
timeout --kill-after=15s 600 docker pull "$IMAGE_REF"
revision=$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')
[ "$revision" = "$TAG" ] || { echo 'Pulled image revision mismatch' >&2; exit 1; }
echo "$SERVICE pulled and verified by digest in $((SECONDS - started))s: $TAG"
