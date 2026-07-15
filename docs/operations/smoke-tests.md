# Smoke Tests

Sprint 5.5E adds a small API smoke suite for release validation. It is intentionally deterministic and does not call OpenAI.

## Scope

- Backend actuator health, liveness, and readiness.
- UniMock actuator health.
- Seeded local auth login.
- Authenticated `/api/v1/auth/me`.
- Task list backward-compatible array response.
- Task list paginated response.
- Assistant conversation start, deterministic interpretation, confirmation, and created task lookup.
- Sprint 7 metadata registration through `POST /api/v1/assistant/conversations/{conversationId}/attachments`.
- `REGISTERED` status and `storageReference = null` for metadata-only attachments.
- Registered attachment references through assistant `attachmentIds`.
- Confirmation idempotency for the registered-attachment path.
- Task attachment metadata/provenance read through `GET /api/v1/tasks/{taskId}/attachments`.
- Negative check that `LOCAL_METADATA_ONLY` legacy attachments do not create durable task links.
- Negative check that registration alone does not create a task.

The smoke suite intentionally validates public APIs only. Deterministic vision analysis fixtures are not exposed as a production HTTP endpoint solely for smoke testing; their lifecycle, profile gating, import, confidence, and idempotency behavior is covered by backend integration tests.

## Local Usage

Start a compatible local stack, then run:

```bash
BACKEND_URL=http://localhost:8080 UNIMOCK_URL=http://localhost:8090 scripts/smoke/api-smoke.sh
```

The script defaults to the seeded local admin:

- hotel code: `hotel-opai-demo`
- email: `admin@hotelopai.local`
- password: `admin123`

Override `HOTEL_CODE`, `ADMIN_EMAIL`, or `ADMIN_PASSWORD` when needed.

## Docker Smoke Stack

The smoke compose file is separate from the existing local compose file:

```bash
docker compose -f docker/docker-compose.smoke.yml up -d postgres unimock backend
scripts/smoke/api-smoke.sh
docker compose -f docker/docker-compose.smoke.yml down -v
```

The smoke stack sets `ASSISTANT_AI_PROVIDER=deterministic` and `ASSISTANT_AI_FALLBACK_ENABLED=false` so validation does not depend on OpenAI.

## Sprint 7 Assertions

The smoke script fails if any Sprint 7 response exposes unsafe media/provider fields:

- `binary`
- `base64`
- `localUri`
- `localReference`
- `storageReference` in task attachment read responses
- `downloadUrl`
- `providerPayload`
- `providerSecret`

The script also verifies:

- attachment registration returns a server-generated UUID
- registration status is `REGISTERED`
- registration does not create a task
- registered attachment confirmation creates exactly one task attachment link
- retrying confirmation with the same idempotency key does not duplicate the task or link
- legacy `LOCAL_METADATA_ONLY` metadata does not become a durable task attachment
