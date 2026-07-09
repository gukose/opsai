# Docker Readiness

Sprint 5.5E adds Docker build validation for the backend and UniMock services.

## Images

Build backend:

```bash
docker build -f docker/backend.Dockerfile -t hotel-opai-backend:ci .
```

Build UniMock:

```bash
docker build -f docker/unimock.Dockerfile -t hotel-opai-unimock:ci .
```

## Runtime Expectations

The images rely on environment variables for database and integration wiring. They do not embed secrets.

Backend production-like variables:

- `SPRING_PROFILES_ACTIVE`
- `OPS_AI_DB_URL`
- `OPS_AI_DB_USERNAME`
- `OPS_AI_DB_PASSWORD`
- `OPS_AI_UNIMOCK_BASE_URL`
- `OPS_AI_AUTH_JWT_SECRET`
- `ASSISTANT_AI_PROVIDER`
- `ASSISTANT_AI_FALLBACK_ENABLED`
- `OPENAI_API_KEY`, when using OpenAI

UniMock variables:

- `SPRING_PROFILES_ACTIVE`
- `OPS_AI_UNIMOCK_DB_URL`
- `OPS_AI_UNIMOCK_DB_USERNAME`
- `OPS_AI_UNIMOCK_DB_PASSWORD`

## Health Checks

Use these backend probe paths:

- `/actuator/health/liveness`
- `/actuator/health/readiness`

Use this UniMock probe path:

- `/actuator/health`
