# Sprint 16 - Go Live Readiness

## Sprint 16A - Internal Knowledge Assistant UI And External Answer Provider Pilot

Sprint 16A adds an internal operator-facing knowledge assistant and the first
disabled-by-default external knowledge answer provider pilot boundary.

Implemented scope:

- Internal knowledge assistant endpoints remain under
  `/api/v1/internal/knowledge/**` and require `KNOWLEDGE_OPERATIONS`.
- Mobile exposes a Knowledge Assistant tab only for users whose current token
  includes `KNOWLEDGE_OPERATIONS`.
- `InternalDemoKnowledgeAnswerProvider` remains the default answer provider.
- `OpenAiKnowledgeAnswerProvider` is registered as an external provider but is
  disabled by default and blocked in production.
- Provider readiness, lifecycle, endpoint classification, smoke diagnostics,
  diagnostic cleanup, and local fixture smoke tests are available internally.
- Answer requests remain grounded in assembled knowledge context and every
  `ANSWERED` response must cite supplied context.
- Feedback supports `HELPFUL`, `NOT_HELPFUL`, `INSUFFICIENT`, and
  `INCORRECT_SOURCE` without free text.
- Per-operator/hotel request quota checks use retained answer history and store
  only safe quota outcomes.

Out of scope:

- User-facing/public knowledge assistant APIs.
- Autonomous task creation or reservation mutation from answers.
- Production external-provider activation.
- Live OpenAI calls in automated tests or default verification.
- Prompt, provider response, credential, embedding, PMS, reservation, guest, or
  payment data persistence in diagnostics, metrics, or audit.

Configuration defaults keep answer generation disabled unless explicitly
enabled:

```yaml
ops:
  ai:
    knowledge:
      answers:
        enabled: false
        active-provider: internal-demo
        providers:
          internal-demo:
            enabled: true
          openai:
            enabled: false
            credential-reference: OPENAI_API_KEY
            smoke-test-only: true
            smoke-test-enabled: false
            fixture-mode-enabled: false
          external-policy:
            production-prohibited: true
            allowed-profiles: [local, test]
```

## Sprint 16B - Knowledge Assistant Hardening

Sprint 16B completes the internal Knowledge Assistant operating surface.

Implemented scope:

- Durable per-hotel/operator in-flight request coordination in PostgreSQL.
- Safe answer-request lifecycle records with statuses from `REQUESTED` through
  terminal `COMPLETED`, `INSUFFICIENT_CONTEXT`, `FAILED`, `REJECTED`, or
  `ABANDONED`.
- Abandoned active request recovery based on the configured timeout.
- Internal dashboard aggregation for provider readiness, retrieval readiness,
  recent answer outcomes, quota usage, active/abandoned requests, feedback
  distribution, citation-count bands, latency bands, and safe failure
  categories.
- Internal active-request listing, request detail, cancellation, abandoned
  recovery, feedback analytics, and cleanup operations under
  `KNOWLEDGE_OPERATIONS`.
- Mobile Knowledge Assistant operations panel with readiness, quota, in-flight,
  recent outcome, and feedback summaries.

Persisted lifecycle metadata intentionally excludes query text, prompts,
retrieved chunk text, provider responses, credentials, PMS data, reservation
data, guest data, and payment data.

Cancellation is cooperative at the lifecycle boundary. It prevents an active
request record from continuing to consume in-flight capacity after cancellation,
but the current JDK HTTP provider boundary does not expose hard request
interruption for an already running provider call.

Retry remains privacy-constrained: failed history records do not retain query
text, so operator retry requires resubmitting the query. This preserves the
Sprint 15/16 rule that query text is not persisted outside the retained answer
record.

## Goal
Complete production launch readiness for Hotel OpAI.

## Business value
Enables a controlled production launch with clear oper/staational ownership, rollback paths, and support readiness.

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
