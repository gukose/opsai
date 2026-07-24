# Sprint 12 - Reservation Domain and Simulation Foundations

## Sprint 12A - Reservation Domain Foundation

Sprint 12A introduces a provider-neutral reservation domain as the canonical
business representation for reservations, guests, stays, and occupancy.

Implemented architecture:

- Added `com.hotelopai.reservation.domain` with the `Reservation` aggregate,
  value objects, lifecycle states, occupancy, date range, guest, and room
  assignment types.
- Kept PMS models integration-facing under `com.hotelopai.pms.domain`.
- Added `PmsReservationMapper` in the reservation application layer to map
  `PmsReservation` and `PmsGuest` into the canonical aggregate.
- Added `ReservationQueryService` for reservation by external reference,
  property/date-range lookup, active stays, arrivals, departures, and in-house
  guests.
- Added `ReservationRepository` as a future persistence/synchronization port.
- Added deterministic in-memory repository implementation for tests and local
  domain workflows only.
- Preserved PMS as the source of truth for this sprint.
- Added privacy safeguards so domain string output and exceptions avoid guest
  names, notes, raw provider payloads, and credentials.

Sprint 12A deliberately does not add synchronization jobs, webhooks, AI
automation, database persistence, new public APIs, or mobile behavior changes.

Architecture details: `docs/architecture/reservation-domain.md`.

## Sprint 12B - Reservation Persistence and PMS Synchronization Foundation

Sprint 12B adds durable canonical reservation snapshots and explicit PMS
synchronization while keeping PMS as the external source of truth.

Implemented architecture:

- Added PostgreSQL/Flyway persistence for reservation snapshots, guest identity
  rows, room-assignment rows, and sync state.
- Persisted canonical reservation fields only. Provider DTOs, raw PMS payloads,
  credentials, guest names, contact details, and unneeded special-request text
  are intentionally discarded.
- Added optimistic locking and deterministic matching on provider id, external
  reservation reference, and property reference.
- Added `ReservationSynchronizationService` for explicit on-demand sync from
  the active PMS provider.
- Added bounded full-window synchronization for providers without incremental
  cursors.
- Added deterministic upsert outcomes: `CREATED`, `UPDATED`, `UNCHANGED`,
  `SKIPPED_STALE`, and `CONFLICT`.
- Added `reservation_sync_state` for safe sync status, timestamps, failure
  category, window, and outcome counts.
- Added safe reservation events through the existing transactional outbox.
- Added safe sync metrics using provider, operation, outcome, and failure
  category tags only.

Sprint 12B deliberately does not add schedulers, webhooks, outbound PMS writes,
new public APIs, mobile changes, AI automation, or production rollout behavior.

Known limitation: current reservation outbox uniqueness permits one event of
each event type per reservation. That keeps Sprint 12B deterministic but should
be revisited before broad reservation event consumers are added.

## Sprint 12C - Reservation Sync Operations and Run History

Sprint 12C adds secure internal manual synchronization operations and durable
sync-run history while keeping automatic synchronization disabled.

Implemented architecture:

- Added `reservation_sync_run` for sanitized run history.
- Added `reservation_sync_run_lock` for provider/property/window overlap
  prevention.
- Added `ReservationSyncOperationsService` as the internal execution boundary
  around `ReservationSynchronizationService`.
- Added internal endpoints under `/api/v1/internal/reservations`.
- Added `RESERVATION_SYNC_OPERATIONS`, granted to `ADMIN` only by migration and
  auth seed data.
- Added trigger model placeholders for `MANUAL`, `SCHEDULED`, `WEBHOOK`, and
  `RECOVERY`; only manual execution is enabled.
- Added safe property scope labels derived from a hash instead of returning raw
  PMS property identifiers.
- Added explicit retention cleanup service support without scheduling it.
- Added safe sync operation audit events and metrics.

Sprint 12C deliberately does not add a scheduler, webhook receiver, public
reservation sync API, SDK surface, mobile behavior, outbound PMS writes, or PMS
source-of-truth changes.

Architecture details: `docs/architecture/reservation-domain.md`.

## Sprint 12D - Controlled Scheduled Reservation Synchronization

Sprint 12D adds disabled-by-default scheduled reservation synchronization on
top of the Sprint 12C operations boundary.

Implemented architecture:

- Added typed `ops.ai.reservation.sync.schedule` configuration for global
  enablement, provider expectation, date-window strategy, lookback/lookahead
  days, timezone, execution interval, startup delay, maximum runs per
  execution, lease timeout, profile allowlist, and scheduled retention cleanup.
- Added `ReservationSyncScheduler`, which only runs when enabled and invokes
  `ReservationSyncOperationsService` instead of calling synchronization logic
  directly.
- Reused the shared database-backed scheduler lease so only one backend
  instance initiates scheduled sync for the schedule scope.
- Kept the existing reservation overlap lock as the final duplicate-traffic
  guard for provider, property scope, and requested date window.
- Added `reservation_sync_schedule_state` for durable pause/resume state and
  last scheduled attempt/success metadata.
- Added internal operations endpoints for scheduler status, run-now,
  pause, and resume under `RESERVATION_SYNC_OPERATIONS`.
- Added a timezone-aware local-date window policy that avoids DST drift.
- Added bounded scheduled cleanup support for completed sync-run history.
- Added safe scheduler metrics and audit events without exposing guest data,
  reservation references, raw property identifiers, credentials, URLs, or raw
  provider responses.

Scheduled synchronization and scheduled retention cleanup are both disabled by
default. Sprint 12D does not add webhook ingestion, outbound PMS writes, public
reservation sync APIs, SDK surface, mobile behavior, or PMS source-of-truth
changes.

Architecture details: `docs/architecture/reservation-domain.md`.

## Sprint 12E - PMS Reservation Webhook Ingestion Foundation

Sprint 12E adds secure, disabled-by-default reservation webhook ingestion.

Implemented architecture:

- Added provider-neutral webhook models for provider id, event id, category,
  redacted property scope, external entity hash, timestamps, processing status,
  retry metadata, safe failure category, and safe metadata.
- Added an Apaleo webhook adapter in the Apaleo integration package.
- Added `reservation_webhook_inbox` for durable deduplication, processing
  status, retry/recovery, and safe audit correlation.
- Added request-size and content-type guards.
- Added credential-reference token verification for Apaleo webhooks because
  Apaleo documentation recommends a unique URL token and does not document a
  cryptographic request-signature header for this flow.
- Added duplicate detection by provider/event id and payload fingerprint
  recording.
- Added `ReservationWebhookProcessingService`, which processes verified inbox
  records by invoking `ReservationSyncOperationsService` with trigger `WEBHOOK`.
- Added bounded retry metadata and abandoned `PROCESSING` recovery.
- Added internal inbox operations for listing, detail, retry, bounded batch
  processing, and cleanup under `RESERVATION_SYNC_OPERATIONS`.
- Added safe webhook metrics and audit actions.

Webhook payloads are never trusted as canonical reservation data. They trigger
bounded PMS reads; canonical reservation snapshots are still updated only by
the existing synchronization boundary.

Webhook ingestion and processing are disabled by default. Sprint 12E does not
add outbound PMS updates, a message broker, public reservation APIs, SDK
surface, mobile behavior, or PMS source-of-truth changes.

Architecture details: `docs/architecture/reservation-domain.md`.

## Sprint 12F - Scheduled Webhook Processing and Operational Activation

Sprint 12F completes the disabled-by-default operational path for webhook inbox
processing.

Implemented architecture:

- Added `ReservationWebhookProcessingScheduler`, which uses the shared
  distributed scheduler-lock infrastructure and calls
  `ReservationWebhookProcessingService` only.
- Added durable pause/resume state and sanitized scheduler status for webhook
  processing through the existing reservation internal operations surface.
- Added operator-triggered batch execution that is audited separately from
  scheduled execution.
- Added deterministic inbox ordering by `next_attempt_at`, `received_at`, and
  inbox id so bounded batches do not permanently starve older eligible records.
- Added terminal `DEAD_LETTER` handling for exhausted retries and explicit
  manual retry support.
- Added disabled-by-default scheduled inbox retention cleanup with a separate
  distributed lease and dead-letter retention window.
- Added startup/runtime activation validation for enabled webhook processing:
  ingestion and processing must be enabled, the active PMS provider must support
  webhooks, a provider adapter must exist, authentication configuration must be
  resolvable, and profile allowlists must match.
- Added safe backlog, dead-letter, scheduler, audit, and metric signals.

Webhook processing, ingestion, and cleanup remain disabled by default. Sprint
12F does not add a message broker, webhook subscription management, outbound
PMS updates, public reservation APIs, SDK surface, or mobile behavior.

Architecture details: `docs/architecture/reservation-domain.md` and
`docs/architecture/background-scheduling.md`.

---

## Goal
Introduce the Simulation Engine using the Master Simulation Dataset.

## Business value
Enables controlled hotel-operations simulations over time for demos, training, regression testing, and future scenario validation.

## Architecture impact
- Adds Simulation Engine as a separate capability from UniMock.
- UniMock remains the PMS simulator; the Simulation Engine drives operational scenarios over time using the Master Simulation Dataset.
- Sprint 12 introduces the engine foundation only, not a full scenario authoring suite.

## Backend tasks
- Implement Simulation Engine domain/application foundation for simulation runs, clocks, steps, emitted operations events, and run state.
- Add adapters that can drive Hotel OpAI workflows and UniMock PMS changes through existing public boundaries.
- Keep engine controls permissioned and separate from production workflows.

## Mobile tasks
- Add minimal internal/admin simulation run visibility if needed for demos.
- Do not expose simulation controls to ordinary hotel staff.

## AI tasks
- Add placeholders for AI-assisted simulation evaluation but keep execution deterministic.
- Do not use AI to decide simulation events in this sprint.

## UniMock tasks
- Allow Simulation Engine to call UniMock update APIs as an external driver.
- Keep UniMock responsible only for PMS state and verification logs.

## Database tasks
- Add Flyway migrations for simulation datasets, simulation runs, simulation steps, emitted events, run logs, and dataset versions.
- Store references to Master Simulation Dataset versions.

## Infrastructure tasks
- Add local simulation configuration, time-control settings, and integration-test fixtures.
- Ensure simulation runs are isolated from production profiles.

## UI tasks
- Add minimal admin-only status views if required.
- Avoid building a full scenario editor.

## Documentation tasks
- Document Simulation Engine vs UniMock boundaries, Master Simulation Dataset format, run lifecycle, and safety controls.

## Testing tasks
- Verify simulation run creation, stepping, pause/resume if included, event emission, and isolation.
- Verify Simulation Engine drives UniMock and Hotel OpAI only through public integration boundaries.

## Risks
- Confusing Simulation Engine with UniMock can blur ownership and create duplicate PMS logic.
- Time simulation can create hard-to-debug data unless runs are isolated and auditable.

## Definition of Done
- Simulation Engine foundation can run a basic deterministic operations simulation over time.
- UniMock remains a separate PMS simulator.
- Simulation runs are auditable, permissioned, and isolated from production behavior.

## Dependencies on previous sprints
- Depends on Sprint 2 UniMock, Sprint 3 Workflow/Task Engine, Sprint 8 notifications, and Sprint 9 reporting foundation.
