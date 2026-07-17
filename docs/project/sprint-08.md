# Sprint 8 - Stabilization Before New Product Features

## Current Stabilization Scope

Sprint 8 is being used first for senior-architect-review follow-up hardening before adding new notification/SLA product features.

Completed/planned stabilization slices:

- Sprint 8A: tenant scope and task write authorization hardening.
- Sprint 8B: assistant concurrency and idempotency hardening.
- Sprint 8C: production role/permission matrix.
- Sprint 8D: operational observability baseline.
- Sprint 8E: transactional outbox and event reliability foundation.
- Sprint 8F: performance and database index hardening.

Sprint 8B backend rules:

- A confirmed assistant draft identity `(conversationId, draftId, draftVersion)` can create at most one task.
- Confirmation idempotency keys still return existing tasks, but draft identity also protects against different-key duplicate confirms.
- Assistant conversation aggregate writes use row-version optimistic concurrency; stale writes return `409 Assistant conversation conflict`.
- Attachment metadata registration accepts an optional `Idempotency-Key`; identical scoped retries return the same `attachmentId`, and same-key metadata mismatches return `409 Attachment registration conflict`.
- Non-idempotent message, reset, import, and confirmation writes are not automatically replayed.

Sprint 8C backend rules:

- Operational APIs are protected by explicit capability permissions instead of
  broad authenticated-only access.
- `POST /api/v1/auth/logout` remains authenticated-only because it is a
  self-session security operation and does not require `AUTH_MANAGE`.
- Stable operational permission codes are seeded through
  `V14__seed_operational_permissions.sql`.
- V14 inserts permission rows and maps permissions only to roles that already
  exist by role code. It does not create production roles or users.
- Existing `ADMIN` roles receive all operational permissions, including
  `DEV_PMS_ACCESS`.
- `MANAGER`, `FRONT_DESK`, `MAINTENANCE`, `HOUSEKEEPING`, and `STAFF` mappings
  are applied only where those role records already exist.
- Dev PMS endpoints remain profile-limited to `local,test` and additionally
  require `DEV_PMS_ACCESS`.
- Tenant isolation remains separate from authorization: missing auth returns
  `401`, missing permission returns `403`, and foreign hotel resources keep
  non-leaking `404` behavior.
- Sprint 8C does not implement assignment-, department-, employee-, team-, or
  category-specific task row filtering. Users with `TASK_READ` currently retain
  hotel-scoped task visibility.

See `docs/architecture/security-permissions.md` for the role matrix and
endpoint-permission mapping.

Sprint 8D backend rules:

- Operational metrics use the existing Micrometer registry with stable
  `hotelopai.*` names and low-cardinality tags only.
- Metrics are internal to the application. Sprint 8D does not expose
  `/actuator/metrics`, Prometheus, tracing, log shipping, or an external APM.
- Health and info actuator exposure remains unchanged.
- Correlation IDs are accepted only when they are trimmed, 128 characters or
  fewer, and match `[A-Za-z0-9._:-]+`; unsafe values are replaced with a UUID.
- Structured logs are limited to non-sensitive operational events. Raw
  assistant text, transcript text, image observation text, prompts, provider
  payloads, tokens, authorization headers, filenames, local URIs, base64, and
  binary data must not be logged.
- Success metrics mean the service operation returned successfully, not that a
  transaction commit hook was observed.
- `hotelopai.task.search.duration` is included at the task search application
  boundary.

See `docs/operations/observability.md` for metric names, tags, and current
observability limitations.

Sprint 8E backend rules:

- Task creation atomically persists the task, task history/log rows, and one
  `TASK_CREATED` row in `operational_outbox`.
- Task creation no longer creates task-created notifications synchronously.
  Notification delivery is eventually consistent after the task transaction
  commits.
- The first outbox event is intentionally limited to `TASK_CREATED`.
- `TASK_CREATED` payload version `1` stores only `payloadVersion`, `taskId`,
  `hotelId`, and `createdAt`.
- The processor reloads the task by `taskId` plus `hotelId` before invoking the
  existing notification routing rules.
- Processing is idempotent through `notifications.source_event_id` with a
  partial unique index. Crash-after-notification retry completes the event
  without creating duplicate notifications.
- Outbox states are `PENDING`, `PROCESSING`, `COMPLETED`, and `FAILED`.
  `FAILED` events are not automatically retried.
- The scheduled processor claims bounded batches with PostgreSQL row locking and
  `FOR UPDATE SKIP LOCKED`, releases the claim transaction, then handles events.
- Retry backoff, max attempts, batch size, polling interval, lock timeout, and
  processor ID are configured under `ops.ai.outbox`.
- Sprint 8E does not add a public outbox API, external broker, Notification
  Engine, SLA alerts, push notifications, email, SMS, or WebSocket delivery.

See `docs/architecture/event-reliability.md` for the transaction flow, retry
behavior, stale-lock recovery, and operational handling.

Sprint 8F backend rules:

- Performance work is measurement-first. V16 was created only after
  representative local PostgreSQL EXPLAIN evidence.
- V16 adds only measured indexes for task created-range reporting, assignment
  filters, active SLA windows, and outbox pending-claim ordering.
- V16 does not add caching, materialized views, `pg_trgm`, GIN search indexes,
  query semantic changes, API contract changes, or mobile changes.
- Notification indexes were not changed because existing recipient/status/time
  indexes matched measured notification query shapes.
- Text search remains the current lower-LIKE behavior. Trigram or generated
  search-vector support is deferred until per-hotel searchable task volume or
  measured latency justifies it.
- Outbox retention remains a follow-up; Sprint 8F adds a pending partial index
  but no archival/deletion job.

See `docs/operations/performance.md` for measured scenarios, before/after plan
summaries, deferred indexes, and candidate latency targets.

## Deferred Original Product Direction

The original notification/SLA scope below is deferred until the stabilization slices are complete and explicitly approved.

# Original Sprint 8 Candidate - Notification Engine + SLA Alerts

## Goal
Implement Notification Engine, push notifications, SLA alerts, and escalation.

## Business value
Keeps staff and managers aware of assigned work, approaching SLA breaches, breached SLAs, and escalations.

## Architecture impact
- Adds notification delivery as a dedicated engine fed by workflow, assignment, and SLA events.
- Establishes escalation policies without hard-coding them into task lifecycle logic.

## Backend tasks
- Implement Notification Engine, notification preferences, delivery records, templates, and escalation rules.
- Emit notifications for assignment, status changes, comments/logs if supported, SLA warning, SLA breach, and escalation.
- Add idempotency for notification event handling.

## Mobile tasks
- Register device tokens, handle push permissions, deep link to task detail, and display notification inbox/history.
- Respect quiet hours or preference settings if included.

## AI tasks
- Support AI-generated notification summaries only through validated templates or disabled placeholders.
- Do not let AI decide escalation policies.

## UniMock tasks
- Provide PMS-backed task context for notification test scenarios.

## Database tasks
- Add Flyway migrations for notification preferences, device tokens, notification events, delivery attempts, templates, and escalation policies.
- Add indexes for due alerts and delivery retries.

## Infrastructure tasks
- Configure push notification provider, credentials, retry policy, dead-letter handling, and local test mode.
- Add scheduled processing for SLA alert evaluation.

## UI tasks
- Add notification permission prompts, inbox/list, unread indicators, and task deep-link behavior.

## Documentation tasks
- Document notification event types, escalation policy model, push setup, retry behavior, and SLA alert thresholds.

## Testing tasks
- Verify push registration and delivery record creation.
- Verify SLA warning, breach, and escalation events.
- Verify idempotent notification processing and retry behavior.

## Risks
- Noisy alerts can reduce adoption.
- Escalation logic must stay configurable for different hotel operations models.

## Definition of Done
- Notifications are generated from task/SLA events and delivered or recorded with retry status.
- Mobile users can receive and navigate from push notifications.
- Escalation rules are configurable and tested.

## Dependencies on previous sprints
- Depends on Sprint 3 task/SLA foundations and Sprint 1 user/role foundations.
