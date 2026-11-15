#!/usr/bin/env bash
# GitHub runner entry point. Keep source upload/build/activation separately visible in Actions.
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

case "${1:?prepare, build or activate}" in
  prepare)
    : "${DEPLOY_KEY:?}" "${KNOWN_HOSTS:?}"
    install -m 600 /dev/null "$RUNNER_TEMP/key"
    printf '%s\n' "$DEPLOY_KEY" > "$RUNNER_TEMP/key"
    printf '%s\n' "$KNOWN_HOSTS" > "$RUNNER_TEMP/known_hosts"
    tar -czf "$RUNNER_TEMP/deploy.tgz" deploy/docker-compose.yml deploy/scripts
    timeout 60 ssh "${opts[@]}" "$remote" "mkdir -p '$RELEASE_DIR'"
    timeout 120 scp "${opts[@]}" "$RUNNER_TEMP/deploy.tgz" \
      "$RUNNER_TEMP/deployment-source/source.tar.gz" "$RUNNER_TEMP/deployment-source/source.tar.gz.sha256" "$remote:$RELEASE_DIR/"
    timeout 60 ssh "${opts[@]}" "$remote" "tar -xzf '$RELEASE_DIR/deploy.tgz' -C '$RELEASE_DIR'"
    ;;
  build|activate)
    phase=$1
    case "$phase" in
      build) script=remote-build-image.sh; limit=2400 ;;
      activate) script=remote-deploy.sh; limit=900 ;;
    esac
    {
      for name in DEPLOY_PATH SERVICE TAG_VAR TAG RELEASE_DIR IMAGE_REF; do
        printf 'export %s=%q\n' "$name" "${!name}"
      done
      cat "deploy/scripts/$script"
    } | timeout --kill-after=15s "$limit" ssh "${opts[@]}" "$remote" bash -s
    ;;
  *) echo 'Unknown release phase' >&2; exit 1 ;;
esac
