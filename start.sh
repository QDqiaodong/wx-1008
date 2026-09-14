#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

check_port() {
  local port="$1"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/tmp/wx-1008-port.$$ 2>/dev/null; then
    echo "端口 $port 已被占用："
    cat /tmp/wx-1008-port.$$
    rm -f /tmp/wx-1008-port.$$
    exit 1
  fi
  rm -f /tmp/wx-1008-port.$$
}

set -a
. ./.env
set +a
check_port "$FRONTEND_PORT"
check_port "$BACKEND_PORT"
check_port "$MYSQL_PORT"
check_port "$REDIS_PORT"

docker compose up -d --build
printf '
前端访问地址: http://localhost:%s
' "$FRONTEND_PORT"
