# opsai

Hotel operations AI workspace.

## Validation

Backend and UniMock tests:

```bash
./gradlew :backend:test :unimock:test
```

Mobile validation without the machine-specific npm test script:

```bash
cd mobile && npx tsc --noEmit
cd mobile && node --no-warnings --test src/api/assistant/assistantMapper.test.mjs
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
