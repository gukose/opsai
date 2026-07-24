# Background Scheduling

Sprint 9C makes scheduled backend jobs safe for single-instance and
multi-instance deployments. Sprint 9D adds lease renewal so a healthy
long-running job can keep its singleton lease beyond the initial lock timeout.

## Scheduled Jobs

Current scheduled jobs:

- `operational_outbox_processor`
  - Trigger: `ops.ai.outbox.poll-interval`, default `PT5S`.
  - Purpose: recover stale outbox locks, claim due outbox events, process
    notifications, and clean old terminal outbox rows.
  - Dependencies: PostgreSQL, task-created outbox handler, notification service.
  - Idempotency: outbox rows are claimed with `FOR UPDATE SKIP LOCKED`;
    notification delivery is protected by `notifications.source_event_id`.

- `task_overdue_scheduler`
  - Trigger: `ops.ai.task.overdue-check-interval-ms`, default `300000`.
  - Purpose: mark overdue tasks according to the existing task lifecycle rules.
  - Dependencies: task repository and task lifecycle service.
  - Idempotency: task lifecycle state checks prevent duplicate overdue
    transitions for tasks already marked overdue.

- `reservation_sync_scheduler`
  - Trigger: `ops.ai.reservation.sync.schedule.execution-interval`, default
    `PT30M`.
  - Purpose: run the configured reservation synchronization policy when
    explicitly enabled.
  - Dependencies: `ReservationSyncOperationsService`, PMS provider registry,
    reservation persistence, sync-run history, and reservation overlap locks.
  - Idempotency: the scheduler lease prevents multiple instances initiating
    the same scheduled policy, and `reservation_sync_run_lock` remains the
    final guard against overlapping provider/property/date-window PMS traffic.
  - Default: disabled through `ops.ai.reservation.sync.schedule.enabled=false`.

- `reservation_webhook_processing_scheduler`
  - Trigger: `ops.ai.reservation.webhooks.schedule.execution-interval`, default
    `PT1M`.
  - Purpose: process a bounded batch of verified PMS reservation webhook inbox
    records when explicitly enabled.
  - Dependencies: `ReservationWebhookProcessingService`, webhook inbox
    persistence, PMS provider webhook adapter, and reservation sync operations.
  - Idempotency: the scheduler lease prevents multiple instances initiating
    the same webhook batch, and inbox claiming with `FOR UPDATE SKIP LOCKED`
    remains the final duplicate-processing guard.
  - Default: disabled through
    `ops.ai.reservation.webhooks.schedule.enabled=false`.

- `reservation_webhook_cleanup_scheduler`
  - Trigger: same scheduled method as webhook processing, but guarded by a
    separate disabled cleanup switch and a separate distributed lease.
  - Purpose: run bounded webhook inbox retention cleanup.
  - Default: disabled through
    `ops.ai.reservation.webhooks.schedule.retention-cleanup-enabled=false`.

These jobs mutate shared operational state, so they run as singleton jobs
across a backend cluster when enabled.

## Distributed Coordination

Singleton execution uses the `scheduler_lock` PostgreSQL table. A job acquires a
lease by inserting or updating its lock row only when `locked_until <= now`.
The lease stores:

- `job_name`
- `locked_until`
- `locked_by`
- `acquired_at`
- `updated_at`

If another instance holds an unexpired lease, execution is skipped. No external
broker, Redis, or scheduler service is required.

Each backend runner has a unique owner value derived from the configured
`instance-id` plus a generated process-local UUID. The owner value is never used
as a metric tag or exposed through APIs.

## Lease Renewal

After a singleton job acquires its distributed lock, the runner starts one
renewal task for that execution. Renewal:

- updates only `locked_until` and `updated_at`
- requires matching `job_name`
- requires matching owner
- requires the existing lease to still be unexpired
- does not change `locked_by`
- does not insert rows or acquire missing locks

If renewal succeeds, another backend instance cannot acquire the job until the
renewed expiry. If renewal fails, the runner records ownership loss and logs an
error. The business action is not interrupted because current jobs do not expose
a cooperative cancellation contract. The action is allowed to complete, and
database-level idempotency remains the final protection for any work already in
progress.

If a backend crashes, renewal stops. The lease expires naturally and another
instance can acquire the job after `locked_until`.

## Overlap Prevention

Each backend instance also keeps a non-blocking local guard per job. If the same
job is already running in that JVM, the next scheduled invocation is skipped and
counted as an overlap skip. The guard never waits indefinitely.

## Shutdown

The shared runner stops accepting new work during bean destruction. Active jobs
are allowed to finish until `ops.ai.scheduler.shutdown-timeout` elapses. Active
jobs continue renewing while they are allowed to finish. If the timeout expires,
the renewal scheduler is stopped and the lease expires naturally. Job actions
keep their existing transactional semantics; outbox rows remain retryable
through normal stale-lock recovery if an instance exits mid-process.

## Configuration

Global scheduler properties:

- `ops.ai.scheduler.enabled`, default `true`
- `ops.ai.scheduler.instance-id`, default `backend`
- `ops.ai.scheduler.default-lock-timeout`, default `PT10M`
- `ops.ai.scheduler.shutdown-timeout`, default `PT30S`
- `ops.ai.scheduler.lease-renewal-enabled`, default `true`
- `ops.ai.scheduler.lease-renewal-interval`, default `PT1M`
- `ops.ai.scheduler.lease-renewal-safety-margin`, default `PT30S`

The renewal interval must be positive, shorter than the effective lock timeout,
and short enough that `lease-renewal-interval + lease-renewal-safety-margin` is
also shorter than the effective lock timeout. Invalid values fail with a clear
configuration error when the runner starts or when a job-specific timeout is
used.

Task overdue scheduler properties:

- `ops.ai.task.overdue.enabled`, default `true`
- `ops.ai.task.overdue.lock-timeout`, default `PT10M`
- `ops.ai.task.overdue-check-interval-ms`, default `300000`

Reservation sync scheduler properties:

- `ops.ai.reservation.sync.schedule.enabled`, default `false`
- `ops.ai.reservation.sync.schedule.execution-interval`, default `PT30M`
- `ops.ai.reservation.sync.schedule.startup-delay`, default `PT2M`
- `ops.ai.reservation.sync.schedule.lock-timeout`, default `PT10M`
- `ops.ai.reservation.sync.schedule.lookback-days`, default `1`
- `ops.ai.reservation.sync.schedule.lookahead-days`, default `14`
- `ops.ai.reservation.sync.schedule.timezone`, default `UTC`
- `ops.ai.reservation.sync.schedule.max-runs-per-execution`, default `1`
- `ops.ai.reservation.sync.schedule.allowed-profiles`, default empty
- `ops.ai.reservation.sync.schedule.retention-cleanup-enabled`, default `false`
- `ops.ai.reservation.sync.schedule.retention-cleanup-max-runs`, default `100`

Reservation webhook processing scheduler properties:

- `ops.ai.reservation.webhooks.schedule.enabled`, default `false`
- `ops.ai.reservation.webhooks.schedule.execution-interval`, default `PT1M`
- `ops.ai.reservation.webhooks.schedule.startup-delay`, default `PT2M`
- `ops.ai.reservation.webhooks.schedule.batch-size`, default `10`
- `ops.ai.reservation.webhooks.schedule.max-records-per-execution`, default `10`
- `ops.ai.reservation.webhooks.schedule.lock-timeout`, default `PT5M`
- `ops.ai.reservation.webhooks.schedule.allowed-profiles`, default empty
- `ops.ai.reservation.webhooks.schedule.retention-cleanup-enabled`, default
  `false`
- `ops.ai.reservation.webhooks.schedule.cleanup-max-records`, default `100`
- `ops.ai.reservation.webhooks.schedule.dead-letter-retention`, default `P90D`
- `ops.ai.reservation.webhooks.schedule.cleanup-lock-timeout`, default `PT5M`

Webhook processing activation also requires
`ops.ai.reservation.webhooks.enabled=true` and
`ops.ai.reservation.webhooks.processing-enabled=true`. The active PMS provider
must declare webhook capability, have a registered webhook adapter, and have
resolvable provider webhook authentication configuration. Temporary external PMS
unavailability is not a startup failure.

Outbox scheduling remains controlled by `ops.ai.outbox.enabled`,
`ops.ai.outbox.poll-interval`, and `ops.ai.outbox.lock-timeout`.

## Observability

Scheduler metrics:

- `hotelopai.scheduler.job.total`
  - tags: `job`, `outcome`, `reason_code`
- `hotelopai.scheduler.job.duration`
  - tags: `job`, `outcome`
- `hotelopai.scheduler.job.active`
  - tags: `status=active`
- `hotelopai.scheduler.lease.renewal.total`
  - tags: `job`, `outcome`, `reason_code`
- `hotelopai.scheduler.lease.renewal.active`
  - tags: `job`

Outcomes include `started`, `success`, `failure`, and `skipped`. Stable reason
codes include `none`, `overlap`, `lock_held`, `shutdown`,
`scheduler_disabled`, and `unexpected_failure`.

Lease renewal outcomes include `attempt`, `success`, `failure`, and
`ownership_lost`. Stable renewal reason codes include `none`,
`ownership_lost`, `renewal_failed`, and `unexpected_failure`.

Logs use:

- `INFO` for job start and finish
- `WARN` for skipped overlap, held distributed lock, or shutdown timeout
- `ERROR` for unexpected job failures or lease ownership loss

Logs and metric tags must not include resource identifiers, payloads, tokens,
or free text.
