# Sprint 15 - Hardening + Release Candidate

## Goal
Complete bug fixing, performance tuning, documentation, regression testing, and release-candidate preparation.

## Business value
Reduces launch risk by stabilizing the full product and operational support model.

## Architecture impact
- Freezes architecture except for fixes required by security, reliability, or performance findings.
- Converts production readiness into a release candidate.

## Backend tasks
- Fix high-priority bugs across auth, tenant isolation, workflow, assistant, AI adapters, notifications, guest channels, reporting, and simulation boundaries.
- Tune slow endpoints and remove unsafe temporary runtime paths.
- Review error handling and audit logging.

## Mobile tasks
- Fix release-blocking mobile defects, accessibility issues, navigation edge cases, offline/error states, and push/deep-link issues.
- Prepare store/release artifacts if applicable.

## AI tasks
- Tune prompts, validation, confidence thresholds, fallback behavior, multilingual responses, and cost controls.
- Confirm AI cannot bypass preview/confirmation or manager approval rules.

## UniMock tasks
- Use UniMock and Simulation Engine runs for regression and demo validation.
- Fix simulator contract issues that affect testing confidence.

## Database tasks
- Review Flyway migration history, indexes, constraints, data-retention jobs, backup/restore rehearsal, and migration performance.
- Remove obsolete tables only through explicit migrations if safe.

## Infrastructure tasks
- Tune production resource limits, autoscaling, alert thresholds, log retention, and CI/CD gates.
- Complete vulnerability/dependency checks and release pipeline hardening.

## UI tasks
- Polish release-critical screens and fix layout, text overflow, state, and accessibility defects.
- Ensure incomplete or risky features are disabled by feature flag.

## Documentation tasks
- Finalize release notes, admin guide, support runbook, troubleshooting guide, API notes, and regression checklist.

## Testing tasks
- Run full regression suite, end-to-end tests, performance tests, security checks, migration tests, backup/restore tests, and mobile release validation.
- Verify known pilot issues are fixed or explicitly accepted.

## Risks
- Late architecture changes can destabilize the release candidate.
- Hidden tenant-isolation or notification defects can be expensive after launch.

## Definition of Done
- Release candidate is built, tested, documented, and accepted against launch criteria.
- Critical and high-priority launch defects are resolved or formally accepted.
- Regression results are documented.

## Dependencies on previous sprints
- Depends on Sprint 14 production infrastructure and all previous product increments.
