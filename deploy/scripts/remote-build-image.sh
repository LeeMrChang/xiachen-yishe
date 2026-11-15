#!/usr/bin/env bash
# Compile inside a resource-limited container before replacing any running service.
set -euo pipefail
: "${DEPLOY_PATH:?}" "${SERVICE:?}" "${TAG:?}" "${RELEASE_DIR:?}" "${IMAGE_REF:?}"
case "$SERVICE" in xc-api|xc-admin) ;; *) exit 1;; esac
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]]
cd "$DEPLOY_PATH"
exec 9>.build.lock
flock -w 60 9
helper="$RELEASE_DIR/deploy/scripts/backend-source.py"
python3 "$helper" preflight "$RELEASE_DIR"
context=$(python3 "$helper" unpack "$RELEASE_DIR" "$TAG")
builder=(env DOCKER_BUILDKIT=0 docker)
help=$("${builder[@]}" build --help)
[[ "$help" == *--memory* ]] || { echo 'Resource-limited Docker classic builder is required' >&2; exit 1; }
started=$SECONDS
echo "Building $SERVICE on server: one CPU, 1 GiB build memory limit"
"${builder[@]}" build --memory=1g --memory-swap=1g --cpu-period=100000 --cpu-quota=100000 \
  --build-arg MAVEN_IMAGE=mirror.ccs.tencentyun.com/library/maven:3.9-eclipse-temurin-21 \
  --build-arg JAVA_IMAGE=mirror.ccs.tencentyun.com/library/eclipse-temurin:21-jre \
  --build-arg MAVEN_MIRROR_URL=https://mirrors.cloud.tencent.com/nexus/repository/maven-public/ \
  --build-arg "MODULE=$SERVICE" --build-arg "REVISION=$TAG" -t "$IMAGE_REF" "$context"
revision=$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')
[ "$revision" = "$TAG" ] || { echo 'Built image revision mismatch' >&2; exit 1; }
echo "$SERVICE built and verified in $((SECONDS - started))s; running service unchanged until activation"
