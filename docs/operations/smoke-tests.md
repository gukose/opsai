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
