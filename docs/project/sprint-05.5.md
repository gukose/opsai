# Sprint 5.5 - Production Readiness

## Goal
Harden the platform before broader hotel experience expansion in Sprint 6.

## Scope split

### Sprint 5.5A - Observability & Health
- Add backend health, readiness, and liveness endpoints.
- Add a safe metrics baseline without exposing sensitive operational data.
- Keep local ports, environment defaults, and startup behavior unchanged.
- Do not change auth rules, persistence, pagination, rate limiting, or mobile behavior.

### Sprint 5.5B - Security Hardening
- Tighten protected backend routes.
- Add production-safe security headers.
- Review secret handling and config validation.

### Sprint 5.5C - Persistence Hardening
- Replace remaining runtime in-memory assistant repositories.
- Add Flyway migrations and repository integration tests.
- Preserve task confirmation idempotency.

### Sprint 5.5D - API Resilience
- Add pagination where list endpoints can grow.
- Add rate limiting for sensitive and write-heavy endpoints.
- Add timeout and retry policy documentation for external integrations.

### Sprint 5.5E - Release Validation
- Add smoke, E2E, and load-test entry points.
- Add Docker build and CI/CD validation.
- Document production runbook and deployment checks.

## Approved implementation
Only Sprint 5.5A is approved for implementation in this pass.

## Definition of Done for 5.5A
- Backend exposes actuator health endpoints for health, liveness, and readiness.
- Backend keeps existing local startup, ports, and environment defaults unchanged.
- Health endpoint behavior is covered by backend tests.
- Full backend test suite passes.
