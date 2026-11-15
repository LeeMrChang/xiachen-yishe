#!/usr/bin/env bash
# GitHub runner entry point. Keep transfer/load/activation separately visible in Actions.
set -euo pipefail
: "${RUNNER_TEMP:?}" "${HOST:?}" "${DEPLOY_USER:?}" "${DEPLOY_PATH:?}" "${SERVICE:?}" "${TAG:?}" "${TAG_VAR:?}" "${GITHUB_REPOSITORY:?}"
[[ "$DEPLOY_PATH" =~ ^/[a-zA-Z0-9/_-]+$ ]]
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]]
case "$SERVICE:$TAG_VAR" in xc-api:XC_API_TAG|xc-admin:XC_ADMIN_TAG) ;; *) exit 1;; esac
RELEASE_DIR="$DEPLOY_PATH/releases/$TAG-$SERVICE"
repository=$(printf '%s' "$GITHUB_REPOSITORY" | tr '[:upper:]' '[:lower:]')
IMAGE_REF="ghcr.io/$repository/$SERVICE:$TAG"
opts=(-i "$RUNNER_TEMP/key" -o BatchMode=yes -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$RUNNER_TEMP/known_hosts" -o ConnectTimeout=15
  -o ServerAliveInterval=15 -o ServerAliveCountMax=4)
remote="$DEPLOY_USER@$HOST"

case "${1:?prepare, upload, load or activate}" in
  prepare)
    : "${DEPLOY_KEY:?}" "${KNOWN_HOSTS:?}"
    install -m 600 /dev/null "$RUNNER_TEMP/key"
    printf '%s\n' "$DEPLOY_KEY" > "$RUNNER_TEMP/key"
    printf '%s\n' "$KNOWN_HOSTS" > "$RUNNER_TEMP/known_hosts"
    tar -czf "$RUNNER_TEMP/deploy.tgz" deploy/docker-compose.yml deploy/scripts
    timeout 60 ssh "${opts[@]}" "$remote" "mkdir -p '$RELEASE_DIR'"
    timeout 120 scp "${opts[@]}" "$RUNNER_TEMP/deploy.tgz" "$remote:$RELEASE_DIR/deploy.tgz"
    timeout 60 ssh "${opts[@]}" "$remote" "tar -xzf '$RELEASE_DIR/deploy.tgz' -C '$RELEASE_DIR'"
    ;;
  upload)
    gzip -df "$RUNNER_TEMP/deployment-image/image.tar.gz"
    archive="$RUNNER_TEMP/deployment-image/image.tar"
    (cd "$RUNNER_TEMP/deployment-image" && sha256sum image.tar > image.tar.sha256)
    timeout 120 ssh "${opts[@]}" "$remote" "if [ -f '$DEPLOY_PATH/image-cache/$SERVICE/image.tar' ] && [ ! -f '$RELEASE_DIR/image.tar' ]; then cp '$DEPLOY_PATH/image-cache/$SERVICE/image.tar' '$RELEASE_DIR/image.tar'; fi"
    printf -v ssh_command '%q ' ssh "${opts[@]}"
    bytes=$(wc -c < "$archive")
    echo "Image archive: $bytes bytes; cached delta basis when available; compression enabled"
    for attempt in 1 2 3; do
      started=$SECONDS
      echo "Image transfer attempt $attempt/3 (limit 900s, idle limit 120s)"
      if timeout --kill-after=15s 900 rsync -z --partial --inplace --checksum --timeout=120 --stats --info=progress2 \
          -e "$ssh_command" "$archive" "$remote:$RELEASE_DIR/image.tar"; then
        echo "Image transfer attempt $attempt succeeded in $((SECONDS - started))s"
        break
      else
        code=$?
        echo "::warning::Image transfer attempt $attempt failed: exit=$code elapsed=$((SECONDS - started))s"
        [ "$attempt" -lt 3 ] || exit "$code"
        sleep $((attempt * 5))
      fi
    done
    # Only publish the checksum after rsync has completely reconstructed the archive.
    timeout 60 scp "${opts[@]}" "$RUNNER_TEMP/deployment-image/image.tar.sha256" "$remote:$RELEASE_DIR/image.tar.sha256"
    ;;
  load|activate)
    phase=$1
    if [ "$phase" = load ]; then script=remote-load-image.sh; limit=300; else script=remote-deploy.sh; limit=900; fi
    {
      for name in DEPLOY_PATH SERVICE TAG_VAR TAG RELEASE_DIR IMAGE_REF; do
        printf 'export %s=%q\n' "$name" "${!name}"
      done
      cat "deploy/scripts/$script"
    } | timeout --kill-after=15s "$limit" ssh "${opts[@]}" "$remote" bash -s
    ;;
  *) echo 'Unknown release phase' >&2; exit 1 ;;
esac
