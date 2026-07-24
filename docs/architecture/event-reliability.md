# Event Reliability

Sprint 8E introduces a PostgreSQL-backed transactional outbox for the first
critical domain event: `TASK_CREATED`.

## Scope

- Implemented event: `TASK_CREATED`.
- No external broker is used.
- No public outbox API is exposed.
- Notification Engine, SLA alerts, push notifications, email, SMS, WebSocket,
  and external delivery infrastructure remain deferred.

## Task Transaction Flow

Task creation uses the existing Task Engine path. In one database transaction it
persists:

- the task
- task state history and task log rows
- one `operational_outbox` row for `TASK_CREATED`

Task creation does not synchronously create a notification. Notification
delivery is eventually consistent after the task transaction commits.

If outbox enqueue fails, the task transaction rolls back. If notification
processing is unavailable later, the committed task remains valid and the outbox
event remains retryable.

Assistant-confirmed task creation uses the same task path and therefore creates
the same outbox event.

## Schema

`operational_outbox` stores:

- event identity and aggregate identity
- server-derived `hotel_id`
- typed JSON payload
- status and attempt metadata
- lock metadata
- sanitized failure code/message
- created/updated/processed timestamps

`notifications.source_event_id` is nullable for compatibility with existing
notification rows. A partial unique index on non-null `source_event_id` prevents
duplicate notification delivery for the same outbox event.

`TASK_CREATED` events are unique by `(event_type, aggregate_type, aggregate_id)`.

## Payload Contract

`TASK_CREATED` payload version `1` contains only:

- `payloadVersion`
- `taskId`
- `hotelId`
- `createdAt`

The payload does not contain task title, description, room number, assistant
content, attachment metadata, media data, provider payloads, tokens, credentials,
or arbitrary serialized domain objects.

The handler reloads the task by `taskId` plus `hotelId` and uses server-side task
state for notification routing.

## Processor

The internal processor:

- selects due `PENDING` events with `next_attempt_at <= now`
- claims a bounded batch using PostgreSQL row locking and `FOR UPDATE SKIP LOCKED`
- marks claimed rows `PROCESSING` with `locked_at` and `locked_by`
- commits the claim transaction before invoking handlers
- marks successful events `COMPLETED`
- returns retryable failures to `PENDING`
- marks exhausted failures `FAILED`

The processor is controlled by `ops.ai.outbox`:

- `enabled`
- `poll-interval`
- `batch-size`
- `max-attempts`
- `initial-retry-delay`
- `retry-multiplier`
- `max-retry-delay`
- `lock-timeout`
- `completed-retention`
- `failed-retention`
- `cleanup-batch-size`
- `processor-id`

Tests disable scheduling and invoke processing directly.

## Retry And Stale Locks

Failures use bounded exponential backoff. Failure records store stable reason
codes and a sanitized fixed message. Raw exception messages, stack traces,
payload JSON, SQL values, and sensitive content are not persisted.

Backoff starts at `initial-retry-delay`, multiplies by `retry-multiplier` after
each failed attempt, and is capped by `max-retry-delay`. `max-attempts` is the
automatic delivery limit; once exhausted, the event moves to `FAILED`.
`updated_at` on a retry or failed transition is the last-attempt timestamp.

`PROCESSING` rows older than `lock-timeout` are recovered to `PENDING` with
cleared lock fields and `next_attempt_at` set to the recovery time. `COMPLETED`
and `FAILED` rows are never recovered for automatic retry.

`FAILED` means automatic attempts are exhausted. Operational recovery should be a
future admin/tooling workflow; direct manual database mutation is not the normal
recovery path.

## Retention

The processor performs bounded cleanup after each processing pass. Cleanup is
idempotent and removes only terminal events:

- `COMPLETED` rows with `processed_at` older than `completed-retention`
- `FAILED` rows with `updated_at` older than `failed-retention`

Cleanup never removes `PENDING`, retrying `PENDING`, or `PROCESSING` rows. The
delete batch is capped by `cleanup-batch-size` so long-running systems can shed
old terminal history without unbounded delete work.

## Notification Idempotency

Task-created notification processing is idempotent:

- the handler creates notifications with `source_event_id = outbox.id`
- the database enforces unique non-null `source_event_id`
- retrying after a crash that occurred after notification persistence but before
  event completion returns the existing notification and marks the event
  `COMPLETED`

Existing task-created notification uniqueness by source task remains compatible.

## Observability

Sprint 8E adds:

- `hotelopai.outbox.event.total`
- `hotelopai.outbox.processing.duration`
- `hotelopai.outbox.state.current`

Allowed outbox tags are low cardinality:

- `operation`
- `event_type`
- `outcome`
- `reason_code`
- `status`

State gauge `status` values are `pending`, `retrying`, `locked`, `completed`,
and `dead_letter`.

Outbox logs are limited to lifecycle transitions: recovery and cleanup at
`INFO`, successful/duplicate processing at `INFO`, retry scheduling at `WARN`,
and terminal failure at `ERROR`. Logs must not include `payload_json`, task
content, assistant text, media data, tokens, credentials, or resource
identifiers.

## Deferred

- external message broker
- multi-service event delivery
- public/admin outbox APIs
- Prometheus/APM integration
- Notification Engine and SLA alert generation
- mobile changes
