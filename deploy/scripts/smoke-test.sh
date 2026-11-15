#!/usr/bin/env bash
# CI-only isolated database; no production credentials or data.
set -euo pipefail
module=${1:?xc-api or xc-admin}
case "$module" in xc-api|xc-admin) ;; *) exit 1;; esac
log=$(mktemp)
java -jar "xc/$module/target/$module-0.1.0-SNAPSHOT.jar" --server.port=18099 >"$log" 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; rm -f "$log"' EXIT
ready=false
for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:18099/actuator/health | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"]=="UP"' 2>/dev/null; then ready=true; break; fi
  kill -0 "$pid" 2>/dev/null || { cat "$log"; exit 1; }
  sleep 2
done
[ "$ready" = true ] || { cat "$log"; exit 1; }
if [ "$module" = xc-api ]; then
  curl -fsS http://127.0.0.1:18099/api/v1/stores | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["code"]==0 and len(d["data"])==3'
  curl -fsS http://127.0.0.1:18099/api/v1/products | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["code"]==0 and d["data"]["total"]==0, "Demo data leaked into production profile"'
else
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18099/api/admin/products)
  [ "$code" = 401 ]
  curl -fsS -H 'Content-Type: application/json' -d '{"username":"admin","password":"ci-admin-only"}' http://127.0.0.1:18099/api/admin/auth/login | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["code"]==0 and d["data"]["token"]'
fi
echo "$module smoke test passed: Flyway, health and business/auth endpoints"
