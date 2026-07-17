#!/usr/bin/env bash

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_DIR="${ROOT_DIR}/scripts/.run"
LOCAL_COMPOSE="${ROOT_DIR}/docker/docker-compose.local.yml"

BACKEND_PORT=8080
UNIMOCK_PORT=8090
MOBILE_PORT=8081
POSTGRES_PORT=5432

BACKEND_URL="http://localhost:${BACKEND_PORT}"
UNIMOCK_URL="http://localhost:${UNIMOCK_PORT}"
MOBILE_URL="http://localhost:${MOBILE_PORT}"

NODE22_BIN="/opt/homebrew/bin/node"
NPM22_BIN="/opt/homebrew/bin/npm"

mkdir -p "${RUN_DIR}"

pid_file() {
  printf '%s/%s.pid\n' "${RUN_DIR}" "$1"
}

log_file() {
  printf '%s/%s.log\n' "${RUN_DIR}" "$1"
}

service_port() {
  case "$1" in
    backend) printf '%s\n' "${BACKEND_PORT}" ;;
    unimock) printf '%s\n' "${UNIMOCK_PORT}" ;;
    mobile) printf '%s\n' "${MOBILE_PORT}" ;;
    postgres) printf '%s\n' "${POSTGRES_PORT}" ;;
    *) return 1 ;;
  esac
}

service_url() {
  case "$1" in
    backend) printf '%s\n' "${BACKEND_URL}" ;;
    unimock) printf '%s\n' "${UNIMOCK_URL}" ;;
    mobile) printf '%s\n' "${MOBILE_URL}" ;;
    *) return 1 ;;
  esac
}

port_pid() {
  local port="$1"
  { lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null || true; } | head -n 1
}

process_command() {
  local pid="$1"
  ps -ef 2>/dev/null | awk -v wanted="${pid}" '$2 == wanted { $1=$2=$3=$4=$5=$6=$7=""; sub(/^[[:space:]]+/, ""); print; exit }'
}

process_cwd() {
  local pid="$1"
  local cwd
  cwd="$(lsof -a -d cwd -p "${pid}" -Fn 2>/dev/null | sed -n 's/^n//p' | head -n 1)"
  printf '%s\n' "${cwd}"
}

pid_alive() {
  local pid="$1"
  [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null
}

service_marker_matches() {
  local service="$1"
  local command="$2"
  case "${service}" in
    backend)
      [[ "${command}" == *"OpsaiApplicationKt"* || "${command}" == *":backend:bootRun"* || "${command}" == *"backend/build"* ]]
      ;;
    unimock)
      [[ "${command}" == *"UniMockApplicationKt"* || "${command}" == *":unimock:bootRun"* || "${command}" == *"unimock/build"* ]]
      ;;
    mobile)
      [[ "${command}" == *"expo start"* || "${command}" == *"node_modules/.bin/expo"* ]]
      ;;
    *)
      return 1
      ;;
  esac
}

is_project_owned_pid() {
  local service="$1"
  local pid="$2"
  local command cwd
  pid_alive "${pid}" || return 1
  command="$(process_command "${pid}")"
  cwd="$(process_cwd "${pid}")"
  [[ -n "${command}" ]] || return 1
  [[ "${cwd}" == "${ROOT_DIR}"* || "${command}" == *"${ROOT_DIR}"* ]] || return 1
  service_marker_matches "${service}" "${command}"
}

describe_pid() {
  local pid="$1"
  printf 'pid=%s command=%s\n' "${pid}" "$(process_command "${pid}")"
}

write_pid() {
  local service="$1"
  local pid="$2"
  printf '%s\n' "${pid}" > "$(pid_file "${service}")"
}

read_pid_file() {
  local service="$1"
  local file
  file="$(pid_file "${service}")"
  [[ -f "${file}" ]] || return 1
  sed -n '1p' "${file}"
}

adopt_service_pid_from_port() {
  local service="$1"
  local port pid
  port="$(service_port "${service}")"
  pid="$(port_pid "${port}")"
  [[ -n "${pid}" ]] || return 1
  if is_project_owned_pid "${service}" "${pid}"; then
    write_pid "${service}" "${pid}"
    return 0
  fi
  return 1
}

ensure_owned_or_free_port() {
  local service="$1"
  local port pid
  port="$(service_port "${service}")"
  pid="$(port_pid "${port}")"
  [[ -z "${pid}" ]] && return 0
  if is_project_owned_pid "${service}" "${pid}"; then
    return 0
  fi
  echo "${service}: port ${port} is occupied by an unknown process; not killing it." >&2
  describe_pid "${pid}" >&2
  return 1
}

stop_service() {
  local service="$1"
  local file pid
  file="$(pid_file "${service}")"

  if [[ ! -f "${file}" ]]; then
    if adopt_service_pid_from_port "${service}"; then
      file="$(pid_file "${service}")"
    else
      echo "${service}: stopped"
      return 0
    fi
  fi

  pid="$(read_pid_file "${service}" || true)"
  if [[ -z "${pid}" ]]; then
    rm -f "${file}"
    echo "${service}: stale pid file removed"
    return 0
  fi

  if ! pid_alive "${pid}"; then
    rm -f "${file}"
    echo "${service}: stale pid file removed"
    return 0
  fi

  if ! is_project_owned_pid "${service}" "${pid}"; then
    echo "${service}: pid file points to a non-owned process; not killing it." >&2
    describe_pid "${pid}" >&2
    return 1
  fi

  kill "${pid}" 2>/dev/null || true
  for _ in {1..20}; do
    if ! pid_alive "${pid}"; then
      break
    fi
    sleep 1
  done

  if pid_alive "${pid}"; then
    echo "${service}: did not stop after SIGTERM; leaving pid ${pid} running" >&2
    return 1
  fi

  rm -f "${file}"
  echo "${service}: stopped"
}

health_status() {
  local url="$1"
  curl -fsS "${url}" 2>/dev/null | grep -q '"status":"UP"'
}

http_status() {
  local url="$1"
  curl -sS -o /dev/null -w '%{http_code}' "${url}" 2>/dev/null || true
}

wait_for_health() {
  local name="$1"
  local url="$2"
  local timeout="$3"
  local log="$4"
  local elapsed=0
  while (( elapsed < timeout )); do
    if health_status "${url}"; then
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  echo "${name}: health check timed out: ${url}" >&2
  [[ -f "${log}" ]] && tail -n 50 "${log}" >&2
  return 1
}

wait_for_http_200() {
  local name="$1"
  local url="$2"
  local timeout="$3"
  local log="$4"
  local elapsed=0
  while (( elapsed < timeout )); do
    if [[ "$(http_status "${url}")" == "200" ]]; then
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  echo "${name}: HTTP 200 check timed out: ${url}" >&2
  [[ -f "${log}" ]] && tail -n 50 "${log}" >&2
  return 1
}

record_listener_pid() {
  local service="$1"
  local port pid
  port="$(service_port "${service}")"
  pid="$(port_pid "${port}")"
  if [[ -z "${pid}" ]]; then
    echo "${service}: no listener found on port ${port}" >&2
    return 1
  fi
  if ! is_project_owned_pid "${service}" "${pid}"; then
    echo "${service}: listener on port ${port} is not owned by this project" >&2
    describe_pid "${pid}" >&2
    return 1
  fi
  write_pid "${service}" "${pid}"
}

postgres_container_status() {
  docker ps -a --filter name=hotel-opai-postgres --format '{{.Names}} {{.Status}} {{.Ports}}' 2>/dev/null | head -n 1
}

ensure_postgres_running() {
  local status
  status="$(postgres_container_status)"
  if [[ "${status}" == hotel-opai-postgres\ Up* ]] && [[ -n "$(port_pid "${POSTGRES_PORT}")" ]]; then
    return 0
  fi
  echo "postgres: starting local container"
  docker compose -f "${LOCAL_COMPOSE}" up -d postgres
}

restart_postgres() {
  echo "postgres: restarting local container without deleting volumes"
  docker compose -f "${LOCAL_COMPOSE}" up -d postgres
  docker compose -f "${LOCAL_COMPOSE}" restart postgres
}

stop_postgres() {
  echo "postgres: stopping local container without deleting volumes"
  docker compose -f "${LOCAL_COMPOSE}" stop postgres
}

assert_mobile_prereqs() {
  if [[ ! -x "${NODE22_BIN}" ]]; then
    echo "mobile: ${NODE22_BIN} is required for Node 22." >&2
    return 1
  fi
  if [[ ! -d "${ROOT_DIR}/mobile/node_modules" || ! -x "${ROOT_DIR}/mobile/node_modules/.bin/expo" ]]; then
    cat >&2 <<EOF
mobile: node_modules is missing or incomplete.
Run:
  cd mobile
  PATH=/opt/homebrew/bin:\$PATH npm ci
EOF
    return 1
  fi
}
