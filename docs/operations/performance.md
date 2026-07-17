# Performance And Index Hardening

Sprint 8F measured tenant-scoped query behavior on PostgreSQL 16.14 using a
disposable local database.

## Fixture

The medium measurement fixture used:

- 50 hotels
- 100,000 tasks total
- 100,000 notifications
- 100,000 outbox rows
- one selected hotel with 10,000 tasks, notifications, and outbox-associated
  rows
- deterministic status, priority, assignment, created-at, updated-at, and SLA
  distributions; SLA deadlines are spread across a seven-day window so the
  two-hour due-soon query is selective

The large-readiness scenario is documented as 500 hotels and 1,000,000 rows per
major table, but it was not loaded into the normal test suite.

Run `ANALYZE` after loading fixtures before using `EXPLAIN`.

## Measured Queries

Measured with `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` where practical and
summarized with text-format plans for review:

- task list for one hotel ordered by `updated_at desc`
- task created-range count for reporting
- status-filtered task list
- priority-filtered task list
- assignment `mine` and role task lists
- active SLA due-soon count
- accessible recent notifications
- unread notification count
- outbox pending claim query
- outbox stale-lock recovery query
- current lower-LIKE task text search

## Indexes Added In V16

`idx_task_hotel_created_at`

- Supports created-in-range dashboard/reporting scans.
- Before: bitmap scan by `idx_task_hotel_id`, then filtered 10,000 hotel rows
  down to 629 rows, about 3.9 ms and 354 shared buffers locally.
- After: index-only scan on `(hotel_id, created_at)`, 629 rows, about 0.39 ms
  and 6 shared buffers locally.
- Write cost: one additional btree entry for every task insert/update affecting
  `hotel_id` or `created_at`.

`idx_task_hotel_assignee_updated_at_desc`

- Supports assignment-specific task lists, especially `assignment=mine`.
- Before: bitmap-and of `assignee_id` and `hotel_id`, then top-N sort, about
  0.48 ms locally.
- After: index scan on hotel, assignee type, assignee ID, and ordered updated
  timestamp, about 0.28 ms locally.
- Role assignment queries were already fast through `idx_task_updated_at` under
  the fixture; this index is retained because `mine` is a selective operational
  workflow and avoids sort.

`idx_task_hotel_active_sla_deadline`

- Partial index for active task SLA windows:
  `where status not in ('COMPLETED', 'CANCELLED')`.
- Supports dashboard/reporting due-soon and overdue predicates when the query
  includes the same active-task predicate.
- Before: broad selected-hotel scans had to filter task status and SLA deadline
  together.
- After: the opt-in V16 script uses `idx_task_hotel_active_sla_deadline` for the
  two-hour due-soon window, returning 89 rows in about 0.13 ms locally.
- Write cost: active/non-terminal task status changes may add/remove index
  entries.

`idx_operational_outbox_pending_created_due`

- Partial index for pending outbox claim ordering:
  `where status = 'PENDING'`.
- Supports `status='PENDING'`, `next_attempt_at <= now`, and
  `order by created_at asc limit batch`.
- Before: bitmap scan due pending rows, heap fetch 1,820 rows, sort by
  `created_at`, about 44 ms and 1,575 shared buffers locally.
- After: index scan in created order, no sort, about 0.69 ms and 73 shared
  buffers locally.
- Write cost: pending outbox rows receive one additional partial-index entry;
  completed/failed rows are not indexed by this partial index.

## Deferred Indexes

`task(hotel_id, updated_at desc)` was measured but not added. The planner kept
using `idx_task_updated_at` for the representative tenant list with low latency
and no sort.

`task(hotel_id, status, updated_at desc)` was deferred. The measured
status-filtered ordered list was already fast through `idx_task_updated_at`, and
broad status GROUP BY scans should not get a permanent index solely by name.

`task(hotel_id, priority, updated_at desc)` was deferred. The measured
priority-filtered ordered list was already fast under the fixture.

Notification indexes were deferred. Existing hotel/recipient/status/created
indexes matched the observed notification list and unread-count plans.

`pg_trgm` and GIN text-search indexes were deferred. Lower-LIKE search currently
scans the selected tenant's rows and took about 13 ms for 10,000 selected-hotel
tasks. Revisit trigram or a generated search vector when a hotel regularly has
more than roughly 50,000 searchable tasks or search latency becomes a measured
problem.

## Redundant Index Review

No existing indexes were dropped in Sprint 8F.

- `idx_task_hotel_id` is still useful as a general hotel filter and remains a
  safe fallback for broad tenant scans.
- `idx_task_updated_at` is still selected by task list/status/priority plans.
- `idx_task_status`, `idx_task_sla_deadline`, and `idx_task_assignee_id` may be
  candidates for future cleanup only after broader production query review.
- V15 outbox indexes remain useful for event aggregate lookups, stale lock
  recovery, and non-claim retry inspection.

## Candidate Latency Targets

These are initial SLO candidates, not proven production guarantees:

- task list/search p95 under 300 ms
- dashboard summary p95 under 500 ms
- reporting p95 under 1 second
- notification list p95 under 300 ms
- outbox claim under 200 ms

The local EXPLAIN runs are not production p95 measurements.

## Outbox Retention Follow-Up

Sprint 8F does not implement outbox archival or deletion.

Recommended follow-up:

- define retention for `COMPLETED` outbox rows
- preserve audit requirements before deletion
- add scheduled cleanup only after retention is approved
- consider partitioning if outbox grows into multi-million-row history

The V16 pending partial index keeps claim scans selective despite completed-row
growth, but storage growth still needs a retention policy.

## Production Migration Note

V16 uses ordinary `CREATE INDEX` in Flyway for the current pre-production/demo
deployment model. Production-scale online index creation may require a separate
non-transactional or concurrent migration strategy.
