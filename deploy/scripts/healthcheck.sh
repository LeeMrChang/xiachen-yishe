#!/usr/bin/env bash
# 夏晨衣舍健康检查：每 5 分钟由 cron 调用
#   */5 * * * * /opt/xiachen/deploy/scripts/healthcheck.sh >> /var/log/xc-healthcheck.cron.log 2>&1
#
# 可选环境变量（写在 cron 的 Environment 或 /etc/environment）：
#   API_HEALTH / ADMIN_HEALTH / API_HOME  覆盖默认检查地址
#   LOG_FILE       失败日志，默认 /var/log/xc-healthcheck.log
#   ALERT_WEBHOOK_URL  企业微信群机器人地址；设置后失败会 POST 文本消息
#   ALERT_NAME     告警前缀，默认「夏晨衣舍」
set -u

API_HEALTH=${API_HEALTH:-http://127.0.0.1:8081/actuator/health}
ADMIN_HEALTH=${ADMIN_HEALTH:-http://127.0.0.1:8082/actuator/health}
API_HOME=${API_HOME:-http://127.0.0.1:8081/api/v1/home}
LOG_FILE=${LOG_FILE:-/var/log/xc-healthcheck.log}

failures=()

check() {
  local name=$1 url=$2 expect=$3
  local response code body
  if ! response=$(curl -sS --max-time 5 -w '\n%{http_code}' "$url" 2>/dev/null); then
    failures+=("$name 无法访问 $url")
    return
  fi
  code=${response##*$'\n'}
  body=${response%$'\n'*}
  if [[ "$code" != "200" ]]; then
    failures+=("$name 返回 HTTP $code")
    return
  fi
  if [[ -n "$expect" && "$body" != *"$expect"* ]]; then
    failures+=("$name 响应异常：${body:0:200}")
  fi
}

check "xc-api 健康" "$API_HEALTH" '"status":"UP"'
check "xc-admin 健康" "$ADMIN_HEALTH" '"status":"UP"'
check "首页接口" "$API_HOME" '"code":0'

if [[ ${#failures[@]} -eq 0 ]]; then
  exit 0
fi

stamp=$(date '+%Y-%m-%d %H:%M:%S')
for line in "${failures[@]}"; do
  echo "$stamp $line" >> "$LOG_FILE" 2>/dev/null || echo "$stamp $line"
done

if [[ -n "${ALERT_WEBHOOK_URL:-}" ]]; then
  message="$(printf '%s\n' "${failures[@]}")"
  content="$(printf '%s 健康检查失败（%s）：\n%s' "${ALERT_NAME:-夏晨衣舍}" "$stamp" "$message")"
  payload=$(printf '%s' "$content" | python3 -c 'import json,sys; print(json.dumps({"msgtype":"text","text":{"content":sys.stdin.read()}}, ensure_ascii=False))' 2>/dev/null)
  if [[ -n "$payload" ]]; then
    curl -fsS --max-time 5 -H 'Content-Type: application/json' -d "$payload" "$ALERT_WEBHOOK_URL" >/dev/null 2>&1 || true
  fi
fi

exit 1
