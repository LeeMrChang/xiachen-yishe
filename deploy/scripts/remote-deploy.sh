#!/usr/bin/env bash
# Execute over SSH stdin. Secrets arrive as shell-quoted exports on stdin, never command-line arguments.
set -euo pipefail
: "${DEPLOY_PATH:?}" "${SERVICE:?}" "${TAG_VAR:?}" "${TAG:?}" "${RELEASE_DIR:?}"
case "$SERVICE:$TAG_VAR" in
  xc-api:XC_API_TAG) port=${XC_API_PORT:-18091} ;;
  xc-admin:XC_ADMIN_TAG) port=${XC_ADMIN_PORT:-18092} ;;
  *) echo "Invalid service/tag variable" >&2; exit 1 ;;
esac
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo 'Expected commit SHA' >&2; exit 1; }
cd "$DEPLOY_PATH"
# API and admin share configuration: serialize even across separate workflows.
exec 9>.deploy.lock
flock -w 300 9
install -m 644 "$RELEASE_DIR/deploy/docker-compose.yml" docker-compose.yml
install -d scripts
install -m 755 "$RELEASE_DIR/deploy/scripts/healthcheck.sh" scripts/healthcheck.sh
[ -s .env ] || { echo 'Production .env is missing' >&2; exit 1; }
# Transfer the CI-built image over SSH; cloud hosts need no registry credentials.
if [ -s "$RELEASE_DIR/image.tar.gz" ]; then
  docker load -i "$RELEASE_DIR/image.tar.gz"
  rm -f "$RELEASE_DIR/image.tar.gz"
fi
previous=$(docker inspect "$SERVICE" --format '{{.Config.Image}}' 2>/dev/null || true)
export "$TAG_VAR=$TAG"
docker compose config --quiet
docker compose images "$SERVICE"
healthy() {
  local attempts=${HEALTH_ATTEMPTS:-60}
  for ((i=0; i<attempts; i++)); do
    running=$(docker inspect "$SERVICE" --format '{{.State.Running}}' 2>/dev/null || true)
    if [ "$running" = true ] && curl -fsS --max-time 3 "http://127.0.0.1:$port/actuator/health" 2>/dev/null | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status")=="UP" else 1)' 2>/dev/null; then
      return 0
    fi
    sleep 3
  done
  return 1
}
if docker compose up -d --no-deps --pull never "$SERVICE" && healthy; then
  # Persist only the successful service tag; later manual restarts keep this release.
  TAG_VAR="$TAG_VAR" TAG="$TAG" python3 - <<'PERSIST'
import os,re
from pathlib import Path
p=Path('.env'); s=p.read_text(); key=os.environ['TAG_VAR']; value=os.environ['TAG']
s=re.sub(r'^'+key+r'=.*$', key+'='+value, s, flags=re.M) if re.search(r'^'+key+r'=',s,re.M) else s.rstrip()+'\n'+key+'='+value+'\n'
t=p.with_suffix('.env.tmp'); t.write_text(s); t.chmod(0o600); t.replace(p)
PERSIST
  echo "$SERVICE deployed $TAG; health UP on :$port"
  exit 0
fi
echo "::error::$SERVICE failed health check; rolling back container when possible" >&2
# Avoid dumping application logs to CI: they can contain configuration secrets.
docker inspect "$SERVICE" --format '{{json .State}}' >&2 || true
if [ -n "$previous" ]; then
  export "$TAG_VAR=${previous##*:}"
  docker compose up -d --no-deps --pull never "$SERVICE"
  healthy || echo '::error::Rollback health check failed' >&2
else
  docker compose stop "$SERVICE"
fi
# Database schema migrations are not automatically reversed.
exit 1
