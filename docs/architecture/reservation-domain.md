# Reservation Domain Foundation

Sprint 12 introduces `com.hotelopai.reservation` as the canonical business
representation for reservations, guests, stays, and occupancy inside Hotel OpAI.

PMS models remain integration-facing. Provider DTOs map into
`com.hotelopai.pms.domain` first, and the reservation application boundary maps
PMS models into canonical reservation aggregates.

## Aggregate Boundary

`Reservation` is the aggregate root. It owns:

- reservation identity
- external PMS reference
- property reference
- primary guest and accompanying guests
- stay date range
- reservation lifecycle state
- physical stay lifecycle state
- room assignment
- occupancy
- source
- operational notes and special requests
- creation and modification timestamps

The aggregate deliberately does not contain vendor-specific fields, provider
DTOs, raw provider payloads, credentials, or transport metadata.

## Domain Types

Core value objects and types:

- `ReservationId`
- `ExternalReservationReference`
- `GuestId`
- `PropertyId`
- `RoomId`
- `ReservationStatus`
- `StayStatus`
- `Occupancy`
- `DateRange`
- `Guest`
- `RoomAssignment`

Invariants include:

- departure must be after arrival
- occupancy cannot be negative or empty
- guest identities must be unique within a reservation
- room assignment periods must fit within the stay period
- cancelled reservations cannot be in-house
- no-show reservations cannot have an active stay

## Lifecycles

Reservation lifecycle and physical stay lifecycle are separate.

Reservation lifecycle:

- `PENDING`
- `CONFIRMED`
- `CANCELLED`
- `NO_SHOW`

Stay lifecycle:

- `NOT_ARRIVED`
- `IN_HOUSE`
- `CHECKED_OUT`

Allowed transitions are modeled on the aggregate:

- pending to confirmed
- confirmed to checked-in
- checked-in to checked-out
- pending or confirmed to cancelled
- confirmed and not-arrived to no-show

Invalid transitions throw `InvalidReservationTransitionException`. Exception
messages are intentionally generic and do not include guest names, notes, room
details, or raw provider data.

## PMS Mapping Boundary

`PmsReservationMapper` maps:

```text
PmsReservation + PmsGuest
  -> Reservation
```

Provider-specific DTOs never map directly into the reservation domain. Apaleo
DTOs still map to PMS models inside the Apaleo integration package first.

Incomplete provider data is rejected explicitly with `ReservationMappingException`.
The mapper requires a provider reservation id, primary guest id, arrival date,
departure date, and property id from the active provider configuration or query
context.

## Query Boundary

`ReservationQueryService` supports current read needs:

- reservation by external reference
- reservations for a property and date range
- active stays
- arrivals
- departures
- in-house guests

For Sprint 12A, PMS remains the source of truth. The query service reads from
the active `PmsProvider` and maps results to canonical reservations at the
application boundary.

## Repository Boundary

`ReservationRepository` defines the persistence/synchronization port for future
sprints.

`InMemoryReservationRepository` exists for deterministic tests and local domain
workflows only.

Sprint 12B adds a PostgreSQL implementation that persists canonical reservation
snapshots. It stores normalized reservation, guest identity, and room-assignment
rows:

- `reservation_snapshot`
- `reservation_guest_snapshot`
- `reservation_room_assignment_snapshot`

The schema persists provider id, external reservation reference, property
reference, lifecycle states, stay dates, occupancy, room assignment, source,
local operational notes, PMS source timestamp, local timestamps, and an
optimistic-lock version. It does not persist provider DTOs, raw provider
payloads, credentials, guest names, contact details, or special requests.

The matching key for PMS snapshots is:

```text
provider id + external reservation reference + property reference
```

That key preserves the local `ReservationId` across refreshes and prevents
duplicate rows for repeated imports of the same PMS reservation.

## Synchronization Boundary

`ReservationSynchronizationService` is an explicit application service. It does
not run on a schedule and no webhook consumer is added through Sprint 12C.

The service:

- resolves the active `PmsProvider`
- requires reservation and guest lookup capabilities
- reads a bounded set of PMS reservations and guests
- maps PMS models into canonical reservations
- upserts snapshots transactionally
- updates `reservation_sync_state`
- emits safe internal reservation events
- returns a sanitized `ReservationSyncSummary`

PMS remains the source of truth for imported reservation facts. The local
database stores a durable operational snapshot for future workflows and
eventing.

Sprint 12C adds `ReservationSyncOperationsService` as the only internal
operations entry point for manual synchronization. Controllers call this service
instead of calling `ReservationSynchronizationService` directly.

Manual sync workflow:

1. Validate trigger, configured property scope, date window, provider readiness,
   and provider capabilities.
2. Create a durable sync-run history record.
3. Acquire a database-backed overlap lock for provider, property scope, and
   requested date window.
4. Invoke the bounded synchronization service.
5. Finalize run status and counters.
6. Release the overlap lock and record safe audit/metric signals.

Automatic trigger types are modeled:

- `MANUAL`
- `SCHEDULED`
- `WEBHOOK`
- `RECOVERY`

Only `MANUAL` can execute while `ops.ai.reservation.sync.operations.enabled-automatic-triggers`
is false. Sprint 12D adds a controlled scheduler-specific path for
`SCHEDULED` runs. That path is internal to the scheduler and still calls
`ReservationSyncOperationsService`, so scheduled execution reuses the same
capability checks, provider readiness checks, date-window guardrails, run
history, overlap lock, audit events, and metrics as manual sync.

No webhook endpoint, queue consumer, outbound PMS update, or public sync API is
added through Sprint 12D. Sprint 12E adds disabled-by-default webhook
ingestion through a durable inbox. Webhooks are treated only as change
notifications; they never directly mutate canonical reservation snapshots.

## Internal Sync Operations

Internal endpoints are under `/api/v1/internal/reservations` and require
`RESERVATION_SYNC_OPERATIONS`:

- `POST /api/v1/internal/reservations/sync`
- `GET /api/v1/internal/reservations/sync-runs`
- `GET /api/v1/internal/reservations/sync-runs/{runId}`
- `GET /api/v1/internal/reservations/sync-state`
- `GET /api/v1/internal/reservations/sync-schedule`
- `POST /api/v1/internal/reservations/sync-schedule/run-now`
- `POST /api/v1/internal/reservations/sync-schedule/pause`
- `POST /api/v1/internal/reservations/sync-schedule/resume`
- `GET /api/v1/internal/reservations/webhooks`
- `GET /api/v1/internal/reservations/webhooks/{eventId}`
- `POST /api/v1/internal/reservations/webhooks/{eventId}/retry`
- `POST /api/v1/internal/reservations/webhooks/process-batch`
- `POST /api/v1/internal/reservations/webhooks/cleanup`

These endpoints are excluded from the public v1 OpenAPI group and generated
SDK. They return provider-neutral DTOs only.

Operational responses do not expose guest data, reservation notes, raw PMS
payloads, credentials, external reservation references, or raw PMS property
identifiers. Property scope is represented as a stable label derived from a
SHA-256 hash, for example `configured:abc123...`.

## Run History And Concurrency

Sprint 12C adds:

- `reservation_sync_run`
- `reservation_sync_run_lock`

Run statuses:

- `REQUESTED`
- `RUNNING`
- `SUCCEEDED`
- `PARTIALLY_SUCCEEDED`
- `FAILED`
- `REJECTED`

Run history records store safe metadata: run id, provider id, property-scope
hash/label, requested date window, trigger, status, timestamps, counters,
bounded page count, safe failure category, and optional actor id.

Overlap prevention is durable and database-backed. The lock repository uses a
PostgreSQL advisory transaction lock around conflict detection, removes expired
locks, and rejects active overlapping windows for the same provider and
property scope. Non-overlapping windows may run independently. Locks are
released after success or failure.

Completed run history has explicit cleanup through
`ReservationSyncOperationsService.cleanupCompletedRuns`. Cleanup is not
scheduled unless `ops.ai.reservation.sync.schedule.retention-cleanup-enabled`
is explicitly enabled. It preserves active `REQUESTED` and `RUNNING` records,
deletes completed records older than the configured retention threshold in
bounded batches, and emits safe audit/metric signals.

## Controlled Scheduled Synchronization

Sprint 12D adds scheduled synchronization that is disabled by default:

```yaml
ops:
  ai:
    reservation:
      sync:
        schedule:
          enabled: false
          property-scope: configured
          window-strategy: LOOKBACK_LOOKAHEAD
          lookback-days: 1
          lookahead-days: 14
          timezone: UTC
          execution-interval: PT30M
          startup-delay: PT2M
          max-runs-per-execution: 1
          lock-timeout: PT10M
          allowed-profiles: []
          retention-cleanup-enabled: false
          retention-cleanup-max-runs: 100
```

The default window policy uses local dates in the configured timezone:

```text
today - lookback-days through today + lookahead-days
```

The stored `DateRange` end date remains exclusive. Local dates are used instead
of fixed-hour arithmetic so daylight-saving transitions do not shift the
reservation window.

Scheduled execution uses `reservation_sync_scheduler` in the shared
`scheduler_lock` table. That lease makes only one backend instance initiate a
scheduled execution for the schedule scope. The reservation overlap lock still
guards the provider/property/date window and remains the final protection
against duplicate PMS traffic.

Durable schedule state is stored in `reservation_sync_schedule_state`:

- paused/resumed state
- last attempted scheduled execution
- last successful scheduled execution
- last safe failure category
- last run id

Pause and resume are runtime operations. Configuration remains the upper-level
enable switch: a paused schedule does not start new scheduled runs, active runs
are not cancelled, and the paused state survives restart. The run-now operation
executes the configured scheduled window immediately but records it as a manual
operational trigger rather than pretending it was automatic.

Scheduler status responses expose only safe data: enabled/paused state,
configured provider id, redacted property-scope label, schedule summary,
timezone, current calculated window, last attempt/success timestamps, coarse
lease state, and safe failure category. They do not expose raw PMS property
identifiers, reservation references, guest data, notes, credentials, URLs, or
provider response bodies.

## Reservation Webhook Ingestion

Sprint 12E adds a provider-neutral webhook ingestion foundation. The receiver
path is:

```text
POST /api/v1/integrations/pms/{providerId}/webhooks
```

The endpoint is excluded from the public OpenAPI group and generated SDK. It is
disabled by default through `ops.ai.reservation.webhooks.enabled=false`.
Processing is separately disabled by default through
`ops.ai.reservation.webhooks.processing-enabled=false`.

Webhook payloads are not trusted as canonical reservation facts. A verified
reservation event is stored in `reservation_webhook_inbox` and later processed
by `ReservationWebhookProcessingService`, which calls
`ReservationSyncOperationsService` with trigger `WEBHOOK`. That service then
performs the bounded PMS read and canonical reservation synchronization.

The inbox stores only durable operational metadata:

- provider id
- provider event id
- event category
- redacted property-scope label and hash
- optional external entity hash
- provider event timestamp
- received timestamp
- processing status
- safe failure category
- retry counters and next-attempt timestamp
- payload fingerprint
- safe metadata such as topic/type
- optional sync run id

Raw webhook bodies, signatures, signing-secret references, guest names,
reservation notes, raw property identifiers, and raw reservation references are
not exposed through operations responses or logs.

Statuses:

- `RECEIVED`
- `VERIFIED`
- `REJECTED`
- `DUPLICATE`
- `PROCESSING`
- `SUCCEEDED`
- `FAILED`
- `IGNORED`
- `DEAD_LETTER`

Apaleo webhook handling follows the official webhook documentation: Apaleo
payloads include an event `id`, `topic`, `type`, property id, timestamp, and
optional entity id, and delivery is at-least-once. Apaleo recommends HTTPS,
IP allowlisting, deduplication by event id, and a unique token in the webhook
URL. No cryptographic request-signature header is documented for this flow, so
the adapter validates a token resolved from a credential reference and compares
it in constant time. Replay protection is provided by event id uniqueness and
payload fingerprint storage.

Event-to-sync policy:

- reservation created, amended, changed, cancelled, checked-in, checked-out,
  and room-assignment events trigger a bounded reservation sync window around
  the provider event date
- health-check and unsupported topics are recorded and ignored safely
- malformed, unsigned/token-invalid, stale timestamp, and unsupported-provider
  requests are rejected without retry

Processing retries are bounded by `ops.ai.reservation.webhooks.max-attempts`.
Transient failures are marked failed with a next-attempt timestamp using bounded
backoff. When attempts are exhausted, the event moves to terminal
`DEAD_LETTER`; automatic processing no longer claims it. An internal operator
may explicitly retry a dead-letter event, which returns it to `VERIFIED` while
retaining the safe failure category and attempt count for diagnostics.
Abandoned `PROCESSING` records can be recovered into retryable failed records.

Sprint 12F adds controlled background processing for verified webhook inbox
records. The scheduler is disabled by default through
`ops.ai.reservation.webhooks.schedule.enabled=false`; ingestion and processing
must also be enabled before scheduled processing can start. Startup validation
requires a webhook-capable active PMS provider, a registered provider adapter,
and resolvable webhook authentication configuration. Temporary PMS
unavailability does not fail startup.

The webhook processing scheduler uses the shared `scheduler_lock` table with
job name `reservation_webhook_processing_scheduler`. Only one backend instance
can initiate a scheduled batch at a time, and inbox claiming with
`FOR UPDATE SKIP LOCKED` remains the final duplicate-processing guard. Cleanup
uses a separate lease named `reservation_webhook_cleanup_scheduler` and remains
disabled unless `ops.ai.reservation.webhooks.schedule.retention-cleanup-enabled`
is explicitly set.

Runtime controls are internal-only:

- `GET /api/v1/internal/reservations/webhooks/schedule`
- `POST /api/v1/internal/reservations/webhooks/schedule/run-now`
- `POST /api/v1/internal/reservations/webhooks/schedule/pause`
- `POST /api/v1/internal/reservations/webhooks/schedule/resume`

They require `RESERVATION_SYNC_OPERATIONS`. Scheduler status exposes only safe
configuration state, pause state, lease state, last execution timestamps, last
processed count, failure category, and backlog counts by status. It does not
expose event ids, raw property ids, external reservation references, payload
fingerprints, signatures, credentials, lock owner values, or guest data.

Processing order is deterministic: eligible records are claimed by
`next_attempt_at`, then `received_at`, then inbox id. This lets older eligible
records make progress while allowing retryable failures to wait for their
backoff instead of blocking fresh verified records.

## Upsert And Stale Data Policy

Synchronization outcomes are deterministic:

- `CREATED` when no local snapshot exists for the provider/external/property key
- `UPDATED` when PMS-owned fields changed
- `UNCHANGED` when the same snapshot is imported again
- `SKIPPED_STALE` when a provider source timestamp is older than the persisted
  source timestamp
- `CONFLICT` when incoming provider data would overwrite locally managed
  operational fields

Equal provider source timestamps are treated deterministically: if PMS-owned
fields match, the snapshot is unchanged; if fields differ, the snapshot is
updated because the PMS remains authoritative for PMS-owned fields.

When provider source timestamps are absent, the service still performs bounded
full-window synchronization, but it cannot make cross-run freshness claims from
provider time. Local optimistic locking still prevents stale concurrent writes.

Clock assumptions:

- provider timestamps are stored as instants when available
- stay windows are stored as local arrival/departure dates
- local created/updated timestamps use persistence precision

## Synchronization State

`reservation_sync_state` stores safe progress metadata per provider and
property:

- sync status
- optional cursor/watermark
- last attempted and successful sync timestamps
- last safe failure category
- source data timestamp
- sync window
- fetched, created, updated, unchanged, stale, and conflict counts
- optimistic version and local timestamps

The current providers do not expose a stable incremental reservation cursor, so
Sprint 12B uses explicit bounded full-window synchronization. The schema is
ready for a future cursor once a provider supports incremental sync.

## Event Model

Sprint 12B adds safe internal events for reservation snapshot changes:

- `RESERVATION_IMPORTED`
- `RESERVATION_UPDATED`
- `RESERVATION_STATUS_CHANGED`
- `GUEST_CHECKED_IN`
- `GUEST_CHECKED_OUT`
- `RESERVATION_CANCELLED`
- `RESERVATION_MARKED_NO_SHOW`
- `ROOM_ASSIGNMENT_CHANGED`

Events are written through the existing transactional outbox. Payloads contain
only reservation id, provider id, property reference, statuses, and timestamps.
They do not include guest names, contact details, notes, room details, raw PMS
payloads, credentials, or request/response bodies.

The current outbox uniqueness rule allows one event of each type per
reservation. This is sufficient for the snapshot foundation and remains a known
limitation before broader reservation event consumers are introduced.

## Privacy Boundary

Guest names, contact details, special requests, operational notes, and
reservation details are personal or operationally sensitive. Routine logs,
metrics, diagnostics, and exception messages must not include these values.

`Reservation`, `Guest`, `GuestId`, and `ExternalReservationReference` avoid
dumping sensitive values in `toString`. Metrics should use safe categories,
provider ids, and lifecycle states rather than reservation or guest identifiers.

Persisted storage intentionally discards:

- guest display names
- contact details
- raw PMS responses
- provider-specific DTO fields
- credentials and credential references
- special requests that are not currently required by product workflows

Local operational notes are retained because they are a Hotel OpAI-owned
operational field. They are not written into routine logs, metrics, event
payloads, or diagnostics.

## Future Path

Future sprints can build on this foundation with:

- scheduled synchronization jobs
- webhook ingestion
- durable outbound change tracking
- richer conflict resolution workflows
- admin-safe operational visibility for sync runs
- retention and deletion automation for personal data
