# Failure Recovery

Sprint 9E adds deterministic backend failure-injection coverage for the task to
outbox to scheduler to notification pipeline. The failure injection mechanism is
test-only and is not compiled into production behavior as a product feature or
runtime toggle.

## Covered Pipeline

The validated flow is:

1. task creation
2. transactional outbox enqueue
3. distributed scheduler execution
4. notification event processing
5. PostgreSQL persistence
6. stale-lock and retry recovery

## Failure Injection Model

Tests inject failures through application interfaces and test doubles:

- `TaskNotificationPublisher`
- `OperationalOutboxRepository`
- `NotificationRepository`
- `SchedulerLockRepository`

There is no Spring profile branching inside business logic. Failures are
configured per deterministic execution point and are consumed once unless a test
explicitly configures more occurrences.

Injected points include:

- before outbox publish
- after outbox publish but before transaction commit
- outbox claim
- outbox completion
- outbox retry persistence
- outbox cleanup
- notification persistence
- scheduler acquisition and renewal through scheduler test doubles

## Transaction Guarantees

Task creation, task history/log creation, and outbox enqueue remain one database
transaction. A failure before outbox publish or after outbox publish but before
commit rolls back task and outbox state together.

Notification delivery is not part of task creation. A notification failure after
task commit leaves the task and outbox event intact and schedules retry through
the outbox processor.

## Outbox Recovery Guarantees

The outbox processor is at-least-once. The effect on task-created notification
delivery is idempotent through `notifications.source_event_id` and existing
source-task uniqueness.

Validated recovery behavior:

- claim failure leaves due events `PENDING`
- notification save failure returns event to retryable `PENDING`
- completion failure after notification persistence is retried without duplicate
  notification
- retry-state persistence failure leaves the row `PROCESSING`; stale-lock
  recovery returns it to `PENDING`
- cleanup failure does not undo completed delivery
- exhausted retries move to `FAILED` in existing outbox coverage

## Scheduler Recovery Guarantees

The scheduler runner prevents same-JVM overlap and uses PostgreSQL leases for
cluster-wide singleton jobs. Lease renewal keeps healthy long-running jobs from
losing ownership. If renewal fails, ownership loss is recorded and logged; the
current operation is allowed to finish because current jobs do not support
cooperative cancellation.

If a process crashes or stops renewing, the lease expires and another instance
can acquire the singleton job. Repository release and renewal are owner-scoped,
so a stale owner cannot release or renew a different instance's lock.

## Observability

Failure paths record existing low-cardinality metrics:

- notification create failure
- outbox retry
- outbox failed/recovered/cleanup outcomes
- scheduler skipped/failure/lease-renewal outcomes

Metric tags must not contain IDs, owner IDs, task content, payload JSON,
exception messages, tokens, or free text. Logs use stable event names and reason
codes; payloads and sensitive values are not logged.

## Operational Expectations

Operators should expect:

- task creation rollback when outbox enqueue fails before commit
- eventual notification delivery after transient notification failures
- no duplicate task-created notification for repeated outbox processing
- retryable events to remain visible as `PENDING` or `PROCESSING`
- terminal poison events to become `FAILED`
- old terminal outbox rows to be removed only by configured retention cleanup

The backend still relies on PostgreSQL availability for task creation and outbox
state transitions. External broker delivery, dead-letter admin APIs, and
operator-triggered event replay remain future work.
