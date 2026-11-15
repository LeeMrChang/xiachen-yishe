#!/usr/bin/env bash
set -euo pipefail
: "${DEPLOY_PATH:?}" "${SERVICE:?}" "${TAG:?}" "${RELEASE_DIR:?}" "${IMAGE_REF:?}"
case "$SERVICE" in xc-api|xc-admin) ;; *) exit 1;; esac
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]]
cd "$DEPLOY_PATH"
exec 9>.deploy.lock
flock -w 60 9
cd "$RELEASE_DIR"
test -s image.tar
test -s image.tar.sha256
sha256sum --check image.tar.sha256
docker load -i image.tar
revision=$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')
[ "$revision" = "$TAG" ] || { echo '::error::Loaded image revision does not match target SHA' >&2; exit 1; }
install -d "$DEPLOY_PATH/image-cache/$SERVICE"
mv -f image.tar "$DEPLOY_PATH/image-cache/$SERVICE/image.tar"
echo "$SERVICE image archive verified and loaded: $TAG"
