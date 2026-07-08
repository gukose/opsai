# Sprint 13 - Pilot Hotel Readiness

## Goal
Prepare Hotel OpAI for a controlled pilot hotel rollout.

## Business value
Moves the product from internal development toward real operational validation with hotel staff and managers.

## Architecture impact
- Hardens tenant onboarding, hotel configuration, operational roles, support processes, and pilot observability.
- Freezes major feature scope for pilot stabilization.

## Backend tasks
- Add pilot hotel onboarding tools, configuration validation, data seeding/import helpers, and support/admin endpoints.
- Review authorization coverage for all pilot workflows.
- Add operational audit exports if needed.

## Mobile tasks
- Polish staff and manager flows for pilot use.
- Add support/contact surfaces, pilot feedback capture, and resilient offline/error states where practical.

## AI tasks
- Tune prompts, confidence thresholds, multilingual behavior, and fallback copy using pilot hotel workflows.
- Ensure AI features can be disabled per tenant if needed.

## UniMock tasks
- Provide pilot rehearsal data that mirrors expected hotel operations.
- Use UniMock to test onboarding and PMS-like workflows before live pilot setup.

## Database tasks
- Add Flyway migrations for pilot configuration, feature flags, feedback, audit exports, or onboarding metadata if needed.
- Validate migration rollback/recovery strategy for pilot environments.

## Infrastructure tasks
- Prepare pilot environment configuration, secrets, backups, monitoring, log retention, and access control.
- Add environment-specific feature flags.

## UI tasks
- Polish pilot-critical screens for clarity, speed, and accessibility.
- Remove or hide incomplete experimental surfaces.

## Documentation tasks
- Create pilot runbook, onboarding checklist, support procedures, training guide, and incident-response notes.

## Testing tasks
- Run end-to-end pilot workflow tests across auth, tasks, assistant, AI, voice/vision if enabled, notifications, dashboards, and guest channels if enabled.
- Run migration rehearsal and backup/restore checks.

## Risks
- Pilot feedback can expose workflow assumptions that require rapid adjustment.
- Feature flags and tenant configuration must prevent unfinished features from leaking into pilot use.

## Definition of Done
- A pilot hotel can be onboarded, configured, trained, monitored, and supported.
- Pilot-critical workflows pass end-to-end tests.
- Rollback, backup, and support paths are documented.

## Dependencies on previous sprints
- Depends on all product increments through Sprint 12 and especially Sprint 1 tenant/auth foundations.
