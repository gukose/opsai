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
