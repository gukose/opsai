# opsai

Hotel operations AI workspace.

## Validation

Backend and UniMock tests:

```bash
./gradlew :backend:test :unimock:test
```

API contract and generated TypeScript SDK checks:

```bash
./gradlew :backend:verifyOpenApiContract :backend:checkOpenApiCompatibility
cd sdk/typescript && npm ci && npm run verify && npm run build && npm test && npm run verify:release-readiness
```

SDK release-readiness only creates local archives under `sdk/typescript/build/package/`; it does not publish packages or create releases.

Mobile validation:

```bash
cd sdk/typescript && npm ci && npm run build
cd mobile && npm ci
cd mobile && npx tsc --noEmit
cd mobile && npm test
```

Mobile validation requires Node 22 or newer.

Docker image builds:

```bash
docker build -f docker/backend.Dockerfile -t hotel-opai-backend:ci .
docker build -f docker/unimock.Dockerfile -t hotel-opai-unimock:ci .
```

Smoke tests:

```bash
docker compose -f docker/docker-compose.smoke.yml up -d postgres unimock backend
scripts/smoke/api-smoke.sh
docker compose -f docker/docker-compose.smoke.yml down -v
```

See `docs/operations/smoke-tests.md`, `docs/deployment/docker.md`, and `docs/deployment/azure-readiness.md`.

PMS provider architecture is documented in `docs/architecture/pms-abstraction.md`.
The default PMS provider is `internal-demo`. The Apaleo sandbox adapter is
available only through explicit `apaleo-sandbox` profile configuration with
environment-provided credential references; normal local and CI runs do not call
external PMS sandboxes.

PMS operational diagnostics are internal-only under `/api/v1/internal/pms` and
require `PMS_OPERATIONS_ACCESS`. They return sanitized provider health,
capability, circuit, and rollout-readiness data without credentials or guest
data.

Reservation domain persistence and explicit PMS synchronization are documented
in `docs/architecture/reservation-domain.md`. Synchronization operations are
internal-only under `/api/v1/internal/reservations` and require
`RESERVATION_SYNC_OPERATIONS`. Controlled scheduled synchronization exists but
is disabled by default. PMS reservation webhook ingestion also exists but is
disabled by default; webhook inbox processing, pause/resume, run-now,
dead-letter, and cleanup operations are internal-only and sanitized.
Reservation-driven task automation is also internal-only and disabled by
default; it consumes canonical reservation events and creates tasks only through
the existing Task Engine when explicitly enabled. Its background scheduler is a
separate disabled-by-default switch under
`ops.ai.reservation.task-automation.schedule.*`, and rule due-time behavior can
be configured per rule without exposing reservation or guest data.
AI-assisted reservation task recommendations are internal-only, advisory, and
disabled by default through `ops.ai.reservation.task-recommendations.enabled`.
They require operator review before any task is created.
Sprint 13D adds provider governance and disabled-by-default scheduled
recommendation generation under
`ops.ai.reservation.task-recommendations.schedule.*`. The current provider is
the deterministic `internal-demo` provider, registered through the same
provider registry future external LLM adapters must use. Scheduled generation
creates only `REVIEW_REQUIRED` recommendations and never approves, applies, or
modifies tasks.
Sprint 13E adds a disabled-by-default OpenAI recommendation provider foundation
behind the same provider registry. External providers receive only the
privacy-gateway outbound context, not guest data, notes, payment data,
reservation references, raw PMS identifiers, webhook payloads, provider DTOs,
prompts with personal data, or credentials.
Sprint 13F adds non-production readiness controls, deterministic local/stub
OpenAI smoke testing, safe durable provider diagnostics, and an operator
runbook. OpenAI remains disabled by default, production activation is blocked,
and no live external LLM traffic occurs unless explicitly configured outside
the default verification flow.
Sprint 14A adds a disabled-by-default non-production external recommendation
pilot. Pilot runs require fresh smoke readiness, property scope allowlists,
daily budgets, and mandatory operator review. Generated pilot recommendations
remain `REVIEW_REQUIRED`; tasks are created only through the existing explicit
approve/apply workflow. Production pilot activation remains blocked.
Sprint 14B adds disabled-by-default scheduled pilot runs under
`ops.ai.reservation.task-recommendations.pilot-schedule.*` and aggregate
operator review analytics. Scheduled pilot runs use the distributed scheduler
lease, respect the same readiness and budget guardrails, and never approve,
apply, or create tasks autonomously. Analytics return only aggregate counts,
rates, and bands.
No public reservation endpoint, SDK surface, or mobile behavior is added.
Focused reservation verification:

```bash
./gradlew :backend:test --tests 'com.hotelopai.reservation.*' --tests 'com.hotelopai.api.reservation.*'
```

Opt-in Apaleo sandbox smoke test:

```bash
APALEO_PROPERTY_ID=<sandbox-property-id> \
APALEO_CLIENT_ID=<sandbox-client-id> \
APALEO_CLIENT_SECRET=<sandbox-client-secret> \
./gradlew :backend:apaleoSandboxSmokeTest -Photelopai.apaleo.sandbox.smoke.enabled=true
```
