#!/usr/bin/env bash
# CI builds and pushes to TCR. The production host only pulls and runs images.
set -euo pipefail
: "${RUNNER_TEMP:?}" "${HOST:?}" "${DEPLOY_USER:?}" "${DEPLOY_PATH:?}" "${SERVICE:?}" "${TAG:?}" "${TAG_VAR:?}" "${IMAGE_REF:?}"
[[ "$DEPLOY_PATH" =~ ^/[a-zA-Z0-9/_-]+$ ]]
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]]
case "$SERVICE:$TAG_VAR" in xc-api:XC_API_TAG|xc-admin:XC_ADMIN_TAG) ;; *) exit 1;; esac
RELEASE_DIR="$DEPLOY_PATH/releases/$TAG-$SERVICE"
[[ "$IMAGE_REF" =~ ^ccr\.ccs\.tencentyun\.com/[a-z0-9][a-z0-9._-]*/$SERVICE@sha256:[0-9a-f]{64}$ ]]
opts=(-i "$RUNNER_TEMP/key" -o BatchMode=yes -o StrictHostKeyChecking=yes
  -o UserKnownHostsFile="$RUNNER_TEMP/known_hosts" -o ConnectTimeout=15
  -o ServerAliveInterval=15 -o ServerAliveCountMax=4)
remote="$DEPLOY_USER@$HOST"

case "${1:?prepare, pull or activate}" in
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
  pull|activate)
    phase=$1
    case "$phase" in
      pull) script=remote-pull-image.sh; limit=900 ;;
      activate) script=remote-deploy.sh; limit=900 ;;
    esac
    {
      names=(DEPLOY_PATH SERVICE TAG_VAR TAG RELEASE_DIR IMAGE_REF)
      if [ "$phase" = pull ]; then
        : "${REGISTRY_USER:?}" "${REGISTRY_PASSWORD:?}"
        names+=(REGISTRY_USER REGISTRY_PASSWORD)
      fi
      for name in "${names[@]}"; do
        printf 'export %s=%q\n' "$name" "${!name}"
      done
      cat "deploy/scripts/$script"
    } | timeout --kill-after=15s "$limit" ssh "${opts[@]}" "$remote" bash -s
    ;;
  *) echo 'Unknown release phase' >&2; exit 1 ;;
esac
