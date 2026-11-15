#!/bin/sh
set -eu
set +x
case "$MODULE" in xc-api|xc-admin) ;; *) exit 1;; esac
case "$IMAGE" in ccr.ccs.tencentyun.com/leemrchang-xiachen-yishe/"$MODULE") ;; *) exit 1;; esac
test "$(git rev-parse HEAD)" = "$SOURCE_SHA"
test "${#SOURCE_SHA}" -eq 40
case "$SOURCE_SHA" in *[!0-9a-f]*) exit 1;; esac
# Only a short-lived token scoped to this image reaches CNB. No TCR password.
export DOCKER_CONFIG
DOCKER_CONFIG=$(mktemp -d)
trap 'rm -rf "$DOCKER_CONFIG"' EXIT
printf '%s' "$DOCKER_AUTH_B64" | base64 -d > "$DOCKER_CONFIG/config.json"
chmod 600 "$DOCKER_CONFIG/config.json"
unset DOCKER_AUTH_B64
docker build --provenance=false --build-arg "MODULE=$MODULE" \
  --label "org.opencontainers.image.revision=$SOURCE_SHA" \
  --label 'org.opencontainers.image.source=https://github.com/LeeMrChang/xiachen-yishe' \
  -t "$IMAGE:$SOURCE_SHA" -f xc/Dockerfile xc
docker push "$IMAGE:$SOURCE_SHA"
