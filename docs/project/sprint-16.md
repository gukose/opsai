# Sprint 16 - Go Live Readiness

## Goal
Complete production launch readiness for Hotel OpAI.

## Business value
Enables a controlled production launch with clear operational ownership, rollback paths, and support readiness.

## Architecture impact
- Confirms the complete system is ready for real hotel operations.
- Locks launch configuration, monitoring, operational runbooks, and release governance.

## Backend tasks
- Run final production smoke checks for auth, tenant isolation, task workflows, assistant, AI adapters, notifications, dashboards, guest channels, reporting, and storage.
- Freeze release branch or release artifact according to the CI/CD process.

## Mobile tasks
- Validate final mobile production build, environment configuration, push behavior, deep links, permissions, and crash reporting.
- Prepare release distribution and support instructions.

## AI tasks
- Validate production AI provider access, usage limits, fallback behavior, multilingual support, and monitoring.
- Confirm emergency disable switches for AI-powered features.

## UniMock tasks
- Keep UniMock available for staging/demo/regression only if approved.
- Ensure production Hotel OpAI points to real PMS integration or explicitly approved production PMS adapter, not demo UniMock.

## Database tasks
- Confirm production Flyway migration state, backups, restore procedure, monitoring, access controls, and retention policies.
- Run final data-integrity and tenant-isolation checks.

## Infrastructure tasks
- Execute go-live checklist for Azure Container Apps, PostgreSQL, Blob Storage, secrets, DNS, TLS, monitoring, logging, alerts, CI/CD, rollback, and on-call access.

## UI tasks
- Final review of launch-critical staff, manager, assistant, guest, and reporting screens.
- Confirm disabled features are hidden and enabled features match documentation.

## Documentation tasks
- Finalize launch checklist, rollback plan, on-call guide, incident process, customer support guide, known issues, and stakeholder sign-off record.

## Testing tasks
- Run final smoke, sanity, migration, backup/restore, alerting, mobile release, and production readiness tests.
- Verify rollback rehearsal or rollback procedure has been accepted.

## Risks
- Launch pressure can hide unresolved operational ownership gaps.
- Production PMS integration assumptions must be explicit if UniMock is not the target.

## Definition of Done
- Go-live checklist is complete and signed off.
- Production systems, monitoring, support, rollback, and release artifacts are ready.
- Hotel OpAI is approved for production launch.

## Dependencies on previous sprints
- Depends on Sprint 15 release candidate acceptance and Sprint 14 production infrastructure.
