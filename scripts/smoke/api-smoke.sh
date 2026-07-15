#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
UNIMOCK_URL="${UNIMOCK_URL:-http://localhost:8090}"
HOTEL_CODE="${HOTEL_CODE:-hotel-opai-demo}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@hotelopai.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

log() {
  printf '[smoke] %s\n' "$1"
}

request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local token="${4:-}"
  local output="$tmp_dir/response.json"
  local headers="$tmp_dir/headers.txt"
  local status

  if [[ -n "$body" && -n "$token" ]]; then
    status="$(curl -sS -X "$method" "$url" -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d "$body" -D "$headers" -o "$output" -w "%{http_code}")"
  elif [[ -n "$body" ]]; then
    status="$(curl -sS -X "$method" "$url" -H "Content-Type: application/json" -d "$body" -D "$headers" -o "$output" -w "%{http_code}")"
  elif [[ -n "$token" ]]; then
    status="$(curl -sS -X "$method" "$url" -H "Authorization: Bearer $token" -D "$headers" -o "$output" -w "%{http_code}")"
  else
    status="$(curl -sS -X "$method" "$url" -D "$headers" -o "$output" -w "%{http_code}")"
  fi

  printf '%s' "$status" > "$tmp_dir/status"
}

expect_status() {
  local expected="$1"
  local label="$2"
  local actual
  actual="$(cat "$tmp_dir/status")"
  if [[ "$actual" != "$expected" ]]; then
    log "$label failed: expected HTTP $expected, got HTTP $actual"
    cat "$tmp_dir/response.json"
    exit 1
  fi
  log "$label OK"
}

json_value() {
  local expression="$1"
  python3 - "$expression" "$tmp_dir/response.json" <<'PY'
import json
import sys

expression = sys.argv[1].split(".")
path = sys.argv[2]
with open(path, "r", encoding="utf-8") as handle:
    data = json.load(handle)
for segment in expression:
    data = data[segment]
print(data)
PY
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local attempt

  for attempt in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "$label ready"
      return
    fi
    sleep 2
  done

  log "$label did not become ready at $url"
  exit 1
}

wait_for_http "$BACKEND_URL/actuator/health" "backend health"
wait_for_http "$BACKEND_URL/actuator/health/liveness" "backend liveness"
wait_for_http "$BACKEND_URL/actuator/health/readiness" "backend readiness"
wait_for_http "$UNIMOCK_URL/actuator/health" "unimock health"

request POST "$BACKEND_URL/api/v1/auth/login" "{\"hotelCode\":\"$HOTEL_CODE\",\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
expect_status 200 "auth login"
access_token="$(json_value accessToken)"
hotel_id="$(json_value user.hotelId)"
user_id="$(json_value user.userId)"

request GET "$BACKEND_URL/api/v1/auth/me" "" "$access_token"
expect_status 200 "current user"

request GET "$BACKEND_URL/api/v1/tasks" "" "$access_token"
expect_status 200 "task list array"
python3 - "$tmp_dir/response.json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
if not isinstance(payload, list):
    raise SystemExit("expected /api/v1/tasks without pagination params to return an array")
PY
initial_task_count="$(python3 - "$tmp_dir/response.json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    print(len(json.load(handle)))
PY
)"

request GET "$BACKEND_URL/api/v1/tasks?page=0&size=10" "" "$access_token"
expect_status 200 "task list page"
python3 - "$tmp_dir/response.json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
for key in ("items", "page", "size", "totalItems", "totalPages", "hasNext", "hasPrevious"):
    if key not in payload:
        raise SystemExit(f"missing paginated task key: {key}")
PY

request POST "$BACKEND_URL/api/v1/assistant/conversations" "{\"hotelId\":\"$hotel_id\",\"userId\":\"$user_id\"}" "$access_token"
expect_status 200 "assistant conversation start"
conversation_id="$(json_value conversationId)"

request POST "$BACKEND_URL/api/v1/assistant/conversations/$conversation_id/attachments" '{"type":"IMAGE","originalFileName":"room-101-ac.jpg","mimeType":"image/jpeg","sizeBytes":204800,"widthPx":1280,"heightPx":720}'
expect_status 401 "unauthenticated attachment registration rejected"

request POST "$BACKEND_URL/api/v1/assistant/conversations/$conversation_id/attachments" '{"type":"IMAGE","originalFileName":"room-101-ac.jpg","mimeType":"image/jpeg","sizeBytes":204800,"widthPx":1280,"heightPx":720}' "$access_token"
expect_status 200 "assistant attachment metadata registration"
attachment_id="$(json_value attachmentId)"
python3 - "$tmp_dir/response.json" <<'PY'
import json
import sys
import uuid

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
uuid.UUID(payload["attachmentId"])
if payload.get("storageStatus") != "REGISTERED":
    raise SystemExit(f"expected REGISTERED storageStatus, got {payload.get('storageStatus')}")
if payload.get("storageReference") is not None:
    raise SystemExit("expected metadata-only registration to return null storageReference")
for key in ("binary", "base64", "localUri", "localReference", "downloadUrl", "providerPayload", "providerSecret"):
    if key in payload:
        raise SystemExit(f"registration response exposed unsafe field: {key}")
PY

request GET "$BACKEND_URL/api/v1/tasks" "" "$access_token"
expect_status 200 "task list after registration"
python3 - "$tmp_dir/response.json" "$initial_task_count" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
if len(payload) != int(sys.argv[2]):
    raise SystemExit("attachment registration created a task")
PY

request POST "$BACKEND_URL/api/v1/assistant/conversations/$conversation_id/messages" "{\"text\":\"Room 101 AC not working\",\"inputType\":\"MIXED\",\"attachmentIds\":[\"$attachment_id\"]}" "$access_token"
expect_status 200 "assistant message interpretation with registered attachment"
python3 - "$tmp_dir/response.json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
if payload.get("state") != "WAITING_FOR_CONFIRMATION":
    raise SystemExit(f"expected WAITING_FOR_CONFIRMATION, got {payload.get('state')}")
preview = payload.get("taskPreview") or {}
if preview.get("roomNumber") != "101":
    raise SystemExit("expected deterministic assistant preview for room 101")
PY

request POST "$BACKEND_URL/api/v1/assistant/conversations/$conversation_id/confirm" '{"idempotencyKey":"smoke-confirm-101"}' "$access_token"
expect_status 200 "assistant task confirmation"
created_task_id="$(json_value createdTaskId)"

request POST "$BACKEND_URL/api/v1/assistant/conversations/$conversation_id/confirm" '{"idempotencyKey":"smoke-confirm-101"}' "$access_token"
expect_status 200 "assistant task confirmation idempotent retry"
python3 - "$tmp_dir/response.json" "$created_task_id" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
if payload.get("createdTaskId") != sys.argv[2]:
    raise SystemExit("idempotent confirmation did not return the original task")
PY

request GET "$BACKEND_URL/api/v1/tasks/$created_task_id" "" "$access_token"
expect_status 200 "created task lookup"

request GET "$BACKEND_URL/api/v1/tasks/$created_task_id/attachments" "" "$access_token"
expect_status 200 "created task attachment lookup"
python3 - "$tmp_dir/response.json" "$attachment_id" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
if not isinstance(payload, list):
    raise SystemExit("expected task attachment response to be a list")
if len(payload) != 1:
    raise SystemExit(f"expected exactly one task attachment link, got {len(payload)}")
attachment = payload[0]
if attachment.get("attachmentId") != sys.argv[2]:
    raise SystemExit("task attachment link did not reference the registered attachment")
if attachment.get("storageStatus") != "REGISTERED":
    raise SystemExit("task attachment did not preserve REGISTERED metadata status")
if attachment.get("sourceType") != "ASSISTANT_MESSAGE":
    raise SystemExit(f"expected ASSISTANT_MESSAGE provenance, got {attachment.get('sourceType')}")
for key in ("binary", "base64", "localUri", "localReference", "storageReference", "downloadUrl", "providerPayload", "providerSecret"):
    if key in attachment:
        raise SystemExit(f"task attachment response exposed unsafe field: {key}")
PY

request GET "$BACKEND_URL/api/v1/tasks/$created_task_id/attachments" "" "$access_token"
expect_status 200 "created task attachment lookup idempotency check"
python3 - "$tmp_dir/response.json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
if len(payload) != 1:
    raise SystemExit("idempotent confirmation created duplicate attachment links")
PY

request POST "$BACKEND_URL/api/v1/assistant/conversations" "{\"hotelId\":\"$hotel_id\",\"userId\":\"$user_id\"}" "$access_token"
expect_status 200 "legacy metadata-only conversation start"
legacy_conversation_id="$(json_value conversationId)"

request POST "$BACKEND_URL/api/v1/assistant/conversations/$legacy_conversation_id/messages" '{"text":"Room 102 sink leaking","inputType":"MIXED","attachments":[{"id":"local-only-attachment","type":"IMAGE","originalFileName":"sink.jpg","mimeType":"image/jpeg","sizeBytes":12345,"widthPx":640,"heightPx":480,"localReference":"device-local-only","storageStatus":"LOCAL_METADATA_ONLY"}]}' "$access_token"
expect_status 200 "legacy metadata-only assistant message interpretation"

request POST "$BACKEND_URL/api/v1/assistant/conversations/$legacy_conversation_id/confirm" '{"idempotencyKey":"smoke-confirm-local-metadata"}' "$access_token"
expect_status 200 "legacy metadata-only task confirmation"
legacy_task_id="$(json_value createdTaskId)"

request GET "$BACKEND_URL/api/v1/tasks/$legacy_task_id/attachments" "" "$access_token"
expect_status 200 "legacy metadata-only task attachment lookup"
python3 - "$tmp_dir/response.json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
if payload != []:
    raise SystemExit("LOCAL_METADATA_ONLY attachment created a durable task attachment link")
PY

log "all smoke checks passed"
