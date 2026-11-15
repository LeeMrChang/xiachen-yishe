#!/usr/bin/env bash
# Read-only diagnostics: never print process arguments, credentials or application logs.
set -euo pipefail
: "${DEPLOY_PATH:?}" "${TAG:?}"
echo 'Transfer process elapsed time and CPU/memory usage:'
ps -C rsync -o pid,etime,pcpu,pmem || true
for service in xc-api xc-admin; do
  archive="$DEPLOY_PATH/releases/$TAG-$service/image.tar"
  if [ -f "$archive" ]; then
    stat --printf="$service archive: %s bytes; modified %y\n" "$archive"
  fi
done
python3 - <<'PY'
import re, subprocess, time
def counters():
    text = subprocess.check_output(['ss', '-tin', '( sport = :22 )'], text=True)
    return [dict(re.findall(r'(bytes_received|bytes_sent|bytes_acked|rtt):([\d./]+)', row))
            for row in text.splitlines() if 'bytes_received:' in row]
before = counters()
time.sleep(5)
after = counters()
print('SSH TCP counters before:', before)
print('SSH TCP counters after 5s:', after)
PY
for port in 18091 18092; do
  curl -sS --max-time 5 -o /dev/null -w "Health :$port HTTP %{http_code}\n" "http://127.0.0.1:$port/actuator/health" || true
done
