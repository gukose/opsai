# Sprint 9 - Manager Dashboard + Live KPIs

## Sprint 9A - Repository-Wide Persistence Timestamp Consistency

### Goal
Establish one backend persistence timestamp precision policy so immediate-return,
reload, and idempotent duplicate paths compare consistently with PostgreSQL.

### Scope
- Use `PersistenceInstant` as the canonical microsecond precision helper.
- Normalize backend-generated timestamps before they are persisted to PostgreSQL.
- Keep JSON-only, reporting, metrics, logging, correlation, UUID-generation, and
  user-supplied deadline timestamps out of scope.
- Add focused save/reload and idempotency regression coverage for affected
  backend persistence paths.

### Migration
No Flyway migration is required. PostgreSQL already stores timestamp values at
microsecond precision; Sprint 9A aligns application-created values with that
storage precision.

### Documentation
See `docs/architecture/persistence-timestamp-precision.md`.

## Sprint 9B - Operational Outbox Recovery & Retention

### Goal
Strengthen the existing PostgreSQL-backed operational outbox for crashes,
retries, concurrent workers, and long-running systems without changing product
API contracts.

### Scope
- Keep `TASK_CREATED` as the only implemented outbox event.
- Preserve `FOR UPDATE SKIP LOCKED` bounded claiming and idempotent notification
  delivery.
- Make retry backoff configurable through `initial-retry-delay`,
  `retry-multiplier`, `max-retry-delay`, and `max-attempts`.
- Keep stale-lock recovery deterministic through `lock-timeout`.
- Add bounded retention cleanup for old `COMPLETED` and `FAILED` events only.
- Add internal state gauges for pending, retrying, locked, completed, and
  dead-letter event counts.

### Migration
No Flyway migration is required. Sprint 9B uses the existing V15 outbox status,
attempt, lock, processed, updated, and failure columns.

### Documentation
See `docs/architecture/event-reliability.md` and
`docs/operations/observability.md`.

## Sprint 9C - Background Job Reliability & Distributed Scheduling

### Goal
Make scheduled backend jobs safe in one-instance and multi-instance
deployments without changing business behavior.

### Scope
- Guard singleton jobs with PostgreSQL-backed scheduler leases.
- Prevent overlapping execution inside a single backend JVM.
- Preserve outbox row claiming, retry, and stale-lock semantics.
- Preserve task overdue lifecycle semantics.
- Add scheduler counters, duration timers, and active-execution gauges.
- Stop accepting new scheduled work during shutdown while allowing active work
  to finish within the configured timeout.

### Migration
Adds V17 `scheduler_lock`, an additive PostgreSQL table used only for scheduler
lease coordination.

### Documentation
See `docs/architecture/background-scheduling.md`.

## Sprint 9D - Scheduler Lease Renewal and Long-Running Job Safety

### Goal
Ensure actively running singleton scheduled jobs keep their distributed lease
while healthy, without preventing crash recovery through lease expiry.

### Scope
- Add owner-only atomic lease renewal to `SchedulerLockRepository`.
- Start one bounded renewal task per active singleton job execution.
- Stop renewal after completion, failure, or shutdown timeout.
- Detect and report lease ownership loss without interrupting current business
  work.
- Preserve same-JVM overlap prevention and PostgreSQL singleton acquisition.
- Reuse V17 `scheduler_lock`; no new migration is required.

### Documentation
See `docs/architecture/background-scheduling.md`.

## Sprint 9E - End-to-End Failure Injection & Operational Resilience

### Goal
Validate the backend task/outbox/scheduler/notification pipeline under
deterministic operational failures without changing product behavior.

### Scope
- Add test-only deterministic failure injection through existing application
  interfaces.
- Validate rollback before task transaction commit.
- Validate retry and idempotency after task commit.
- Validate outbox claim, retry, completion, cleanup, and stale recovery paths.
- Validate scheduler acquisition, renewal, ownership-loss, and shutdown behavior
  through existing scheduler-focused tests.
- Document recovery guarantees and operational expectations.

### Documentation
See `docs/architecture/failure-recovery.md`.

## Goal
Deliver Manager Dashboard, reporting foundation, and live KPIs.

## Business value
Gives managers real-time visibility into hotel operations, workload, SLA health, and recovery priorities.

## Architecture impact
- Adds read-optimized reporting queries and KPI aggregation without changing task ownership rules.
- Establishes dashboard APIs that later Reporting AI can consume.

## Backend tasks
- Implement manager dashboard APIs for workload, open tasks, SLA health, team performance, category breakdowns, and recent escalations.
- Add role-based access for manager-only views.
- Add reporting query services separate from command-side task services.

## Mobile tasks
- Add manager dashboard screens, KPI tiles, filters, drilldowns, and task navigation.
- Preserve staff task flows while exposing manager-only views by permission.

## AI tasks
- Prepare dashboard data contracts for later manager AI reporting.
- Keep AI-generated reports out of scope until Sprint 11.

## UniMock tasks
- Provide PMS context and seeded operational variety for dashboard demos.

## Database tasks
- Add Flyway migrations for reporting snapshots or materialized views if needed.
- Add indexes for KPI queries by hotel, status, assignee, department, category, and SLA due time.

## Infrastructure tasks
- Add performance budgets for dashboard endpoints.
- Add local load fixtures for realistic task and SLA volumes.

## UI tasks
- Implement dense, scannable manager UI for repeated operational use.
- Include empty, loading, error, and filtered states.

## Documentation tasks
- Document KPI definitions, manager permissions, reporting query model, and dashboard endpoint contracts.

## Testing tasks
- Verify KPI calculations.
- Verify manager access controls.
- Verify dashboard filters and drilldowns.
- Verify query performance against representative data.

## Risks
- KPI definitions can be disputed unless documented precisely.
- Heavy dashboard queries can affect operational task performance if not isolated.

## Definition of Done
- Managers can see live operational KPIs and drill into underlying tasks.
- Dashboard APIs are permissioned, tested, and performant enough for pilot usage.

## Dependencies on previous sprints
- Depends on Sprint 3 task/SLA data, Sprint 8 notifications/escalations, and Sprint 1 roles/permissions.
