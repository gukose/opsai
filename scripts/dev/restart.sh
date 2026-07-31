#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/dev/_lib.sh
source "${SCRIPT_DIR}/_lib.sh"

target="all"
with_postgres=false
mobile_clear=false

usage() {
  cat <<'EOF'
Usage:
  ./scripts/dev/restart.sh [backend|unimock|mobile|all] [--with-postgres] [--clear]

Examples:
  ./scripts/dev/restart.sh
  ./scripts/dev/restart.sh backend
  ./scripts/dev/restart.sh mobile --clear
  ./scripts/dev/restart.sh --with-postgres
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    backend|unimock|mobile|all)
      target="$1"
      shift
      ;;
    --with-postgres)
      with_postgres=true
      shift
      ;;
    --clear)
      mobile_clear=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

start_unimock() {
  local log
  log="$(log_file unimock)"
  ensure_owned_or_free_port unimock
  : > "${log}"
  nohup bash -lc "cd '${ROOT_DIR}' && exec env SPRING_PROFILES_ACTIVE=local ./gradlew :unimock:bootRun" > "${log}" 2>&1 < /dev/null &
  write_pid unimock "$!"
  wait_for_health "unimock" "${UNIMOCK_URL}/actuator/health" 90 "${log}"
  record_listener_pid unimock
}

start_backend() {
  local log
  log="$(log_file backend)"
  ensure_owned_or_free_port backend
  : > "${log}"
  nohup bash -lc "cd '${ROOT_DIR}' && exec env SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun" > "${log}" 2>&1 < /dev/null &
  write_pid backend "$!"
  wait_for_health "backend" "${BACKEND_URL}/actuator/health" 120 "${log}"
  wait_for_health "backend liveness" "${BACKEND_URL}/actuator/health/liveness" 30 "${log}"
  wait_for_health "backend readiness" "${BACKEND_URL}/actuator/health/readiness" 30 "${log}"
  record_listener_pid backend
}

start_mobile() {
  local log clear_arg
  log="$(log_file mobile)"
  assert_mobile_prereqs
  ensure_owned_or_free_port mobile

  clear_arg=""
  if [[ "${mobile_clear}" == true ]]; then
    clear_arg="--clear"
  fi

  : > "${log}"
  nohup bash -lc "cd '${ROOT_DIR}/mobile' && exec env PATH='/opt/homebrew/bin:${PATH}' EXPO_PUBLIC_API_BASE_URL='http://localhost:8080' EXPO_PUBLIC_ASSISTANT_API_BASE_URL='http://localhost:8080' EXPO_PUBLIC_ASSISTANT_DATA_SOURCE='backend' EXPO_PUBLIC_APP_ENV='local' npx expo start --web --port 8081 ${clear_arg}" > "${log}" 2>&1 < /dev/null &
  write_pid mobile "$!"
  wait_for_http_200 "mobile" "${MOBILE_URL}" 120 "${log}"
  record_listener_pid mobile

  if grep -q "Failed to get the SHA-1" "${log}" 2>/dev/null; then
    cat >&2 <<EOF
mobile: Metro reported a SHA-1 error. node_modules may be incomplete.
Run:
  cd mobile
  PATH=/opt/homebrew/bin:\$PATH npm ci
EOF
    return 1
  fi
}

restart_service() {
  local service="$1"
  stop_service "${service}"
  case "${service}" in
    unimock) start_unimock ;;
    backend) start_backend ;;
    mobile) start_mobile ;;
  esac
}

if [[ "${with_postgres}" == true ]]; then
  restart_postgres
else
  ensure_postgres_running
fi

case "${target}" in
  backend)
    restart_service backend
    ;;
  unimock)
    restart_service unimock
    ;;
  mobile)
    restart_service mobile
    ;;
  all)
    restart_service unimock
    restart_service backend
    restart_service mobile
    ;;
esac

cat <<EOF
Local stack ready

Postgres: http://localhost:5432
UniMock:  ${UNIMOCK_URL}  UP
Backend:  ${BACKEND_URL}  UP
Mobile:   ${MOBILE_URL}  HTTP 200

Logs:
- scripts/.run/unimock.log
- scripts/.run/backend.log
- scripts/.run/mobile.log
EOF
