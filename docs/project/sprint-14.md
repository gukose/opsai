# Sprint 14 - Production Infrastructure + Azure

## Goal
Prepare production deployment on Azure with Container Apps, Blob Storage, monitoring, security, performance, and CI/CD.

## Business value
Creates the operational platform needed to run Hotel OpAI reliably outside pilot-only environments.

## Architecture impact
- Moves runtime strategy from local/pilot setup to production-grade cloud deployment.
- Completes production infrastructure work without treating infrastructure as a separate sprint category.

## Backend tasks
- Add production-ready health checks, readiness checks, configuration validation, graceful shutdown, and structured logging.
- Review API security headers, rate limits, tenant isolation, and audit coverage.

## Mobile tasks
- Configure production API environments, release channels, crash reporting, and production build settings.
- Validate push, voice, vision, and guest-channel configuration in production-like environments.

## AI tasks
- Configure production OpenAI and voice/vision provider settings through secrets.
- Enforce production cost limits, rate limits, retries, and monitoring alerts.

## UniMock tasks
- Decide whether UniMock is deployed only for staging/demo or excluded from production runtime.
- Configure staging UniMock with isolated data and access controls if deployed.

## Database tasks
- Configure managed PostgreSQL, Flyway migration execution strategy, backup policy, retention, PITR expectations, and database monitoring.
- Review indexes and query plans for production workloads.

## Infrastructure tasks
- Deploy Azure Container Apps for backend and supporting services.
- Configure Azure Blob Storage for attachments.
- Configure monitoring, logging, alerts, dashboards, secrets, network rules, CI/CD pipelines, and environment promotion.
- Add performance and load testing infrastructure.

## UI tasks
- Validate production mobile UI across target devices.
- Confirm external links, deep links, push navigation, and attachment rendering.

## Documentation tasks
- Document deployment runbook, CI/CD flow, environment variables, secret rotation, monitoring dashboards, incident response, and production support responsibilities.

## Testing tasks
- Run production-like smoke tests, load tests, security checks, migration dry runs, backup/restore checks, and release pipeline validation.
- Verify monitoring alerts fire for representative failures.

## Risks
- Cloud configuration drift can break reproducibility without automated deployment.
- Production secrets, storage, and logs can expose sensitive hotel data if not reviewed carefully.

## Definition of Done
- Production-like Azure environment can be deployed through CI/CD.
- Backend, mobile configuration, database, Blob Storage, monitoring, logging, and alerts are validated.
- Security and performance checks meet launch criteria.

## Dependencies on previous sprints
- Depends on pilot readiness from Sprint 13 and storage/attachment foundations from Sprint 7.
