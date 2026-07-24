# Persistence Timestamp Precision

## Policy

Backend PostgreSQL timestamp columns use microsecond precision. JVM `Instant`
values can carry nanoseconds, so operational timestamps generated before
persistence must be normalized before they are saved or returned from save
methods.

The canonical utility is:

- `PersistenceInstant.toPersistencePrecision(instant)`
- `PersistenceInstant.toPersistencePrecisionOrNull(instant)`
- `PersistenceInstant.now(clock)`

The transformation truncates to microseconds and keeps UTC `Instant` semantics.
It is idempotent and does not use a global clock.

## Why This Exists

Without normalization, a service can return a newly-created object containing a
nanosecond `Instant`, while a later PostgreSQL reload returns the same value at
microsecond precision. That makes equality and idempotent duplicate paths
nondeterministic across JVM, OS, and PostgreSQL precision behavior.

## In Scope

Normalize timestamps created or assigned by backend code when they are stored in
PostgreSQL timestamp columns, including:

- task creation, status transitions, assignments, history, and logs
- task attachment links
- assistant conversation row `created_at` and `updated_at`
- assistant task confirmation `created_at`
- assistant attachment registration timestamps
- notification creation and mark-read timestamps
- vision analysis and vision import timestamps
- operational outbox enqueue, claim, retry, processing, and stale-lock timestamps
- authentication refresh-session timestamps
- persisted audit fields for auth, hotel, and employee records

For JDBC repositories that insert and immediately return domain objects, the
returned object must carry the normalized value. Normalizing only the
`Timestamp.from(...)` call is not enough.

## Excluded

Do not normalize these solely for persistence policy:

- assistant message timestamps stored only inside `messages_json`
- dashboard/reporting `generatedAt`
- metrics, logging, tracing, and correlation timestamps
- UUIDv7 generation input
- transient policy-calculation timestamps
- user-supplied task deadlines
- external/provider timestamps unless stored in PostgreSQL timestamp columns

## Query Boundaries

When a timestamp is directly compared to PostgreSQL timestamp columns and then
stored as part of the same operation, normalize it once at the operation
boundary. This keeps retry and due-event checks aligned with stored microsecond
values without processing events early.

## Tests

Use fixed instants in tests. For equality-sensitive persistence tests, use
microsecond values or assert that immediate-return and reload paths match.
Do not hide production precision bugs by normalizing only expected assertion
values.

## Migration

No database migration is required for this policy. PostgreSQL already stores
existing timestamp values at supported precision. The change is an application
creation-boundary and adapter-return consistency rule.
