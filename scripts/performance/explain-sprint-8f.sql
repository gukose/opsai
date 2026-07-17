-- Sprint 8F local performance fixture and EXPLAIN script.
--
-- Use only with a disposable local database. Do not run against production.
-- The fixture uses recognizable sprint-8f hotel codes and deterministic UUID
-- ranges. Cleanup at the bottom removes only this generated fixture data.

\pset pager off
\timing on

select version() as postgres_version;

insert into hotel (id, version, created_at, updated_at, code, name, status)
select ('00000000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid,
       0,
       '2026-01-01T00:00:00Z'::timestamptz,
       '2026-01-01T00:00:00Z'::timestamptz,
       'sprint-8f-hotel-' || i,
       'Sprint 8F Hotel ' || i,
       'ACTIVE'
from generate_series(1, 50) as i
on conflict (id) do nothing;

insert into task (
    id, hotel_id, version, created_at, updated_at, intent_type, source, title,
    description, room_number, priority, status, sla_deadline, assignee_type,
    assignee_id, assignee_display_name, assigned_at, started_at, completed_at,
    cancelled_at, overdue_at
)
select ('10000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
       ('00000000-0000-4000-8000-' || lpad((case when gs <= 10000 then 1 else ((gs - 10001) % 49) + 2 end)::text, 12, '0'))::uuid,
       0,
       ('2026-07-01T00:00:00Z'::timestamptz + ((gs % 14) || ' hours')::interval + ((gs % 1440) || ' minutes')::interval),
       ('2026-07-15T00:00:00Z'::timestamptz - ((gs % 100000) || ' seconds')::interval),
       (array['MAINTENANCE','GUEST_REQUEST','HOUSEKEEPING','SECURITY','FRONT_DESK'])[(gs % 5) + 1],
       (array['MANUAL','ASSISTANT'])[(gs % 2) + 1],
       'Sprint 8F task ' || gs,
       'Synthetic deterministic task for Sprint 8F measurement ' || gs,
       ((gs % 700) + 100)::text,
       (array['LOW','MEDIUM','HIGH','URGENT'])[(gs % 4) + 1],
       (array['CREATED','ASSIGNED','STARTED','IN_PROGRESS','WAITING','OVERDUE','COMPLETED','CANCELLED'])[(gs % 8) + 1],
       ('2026-07-15T00:00:00Z'::timestamptz + (((gs * 7919) % 604800) || ' seconds')::interval),
       case when gs % 5 = 0 then null when gs % 2 = 0 then 'USER' else 'TEAM' end,
       case when gs % 5 = 0 then null when gs % 2 = 0 then ('20000000-0000-4000-8000-' || lpad((gs % 200)::text, 12, '0')) else (array['ADMIN','MANAGER','MAINTENANCE','HOUSEKEEPING','FRONT_DESK'])[(gs % 5) + 1] end,
       case when gs % 5 = 0 then null else 'Assignee ' || (gs % 200) end,
       case when gs % 5 = 0 then null else '2026-07-10T00:00:00Z'::timestamptz end,
       case when gs % 8 in (2,3,4,5,6) then '2026-07-11T00:00:00Z'::timestamptz else null end,
       case when gs % 8 = 6 then '2026-07-16T00:00:00Z'::timestamptz else null end,
       case when gs % 8 = 7 then '2026-07-16T00:00:00Z'::timestamptz else null end,
       case when gs % 8 = 5 then '2026-07-16T00:00:00Z'::timestamptz else null end
from generate_series(1, 100000) as gs
on conflict (id) do nothing;

insert into notifications (
    id, hotel_id, version, created_at, updated_at, recipient_user_id,
    recipient_role_code, type, status, title, body, source_task_id, read_at,
    source_event_id
)
select ('30000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
       ('00000000-0000-4000-8000-' || lpad((case when gs <= 10000 then 1 else ((gs - 10001) % 49) + 2 end)::text, 12, '0'))::uuid,
       0,
       ('2026-07-15T00:00:00Z'::timestamptz - ((gs % 100000) || ' seconds')::interval),
       ('2026-07-15T00:00:00Z'::timestamptz - ((gs % 100000) || ' seconds')::interval),
       case when gs % 2 = 0 then ('20000000-0000-4000-8000-' || lpad((gs % 200)::text, 12, '0'))::uuid else null end,
       case when gs % 2 = 0 then null else (array['ADMIN','MANAGER','MAINTENANCE','HOUSEKEEPING','FRONT_DESK'])[(gs % 5) + 1] end,
       'TASK_CREATED',
       case when gs % 3 = 0 then 'READ' else 'UNREAD' end,
       'Task created',
       'Task created notification',
       null,
       case when gs % 3 = 0 then '2026-07-15T01:00:00Z'::timestamptz else null end,
       null
from generate_series(1, 100000) as gs
on conflict (id) do nothing;

insert into operational_outbox (
    id, event_type, aggregate_type, aggregate_id, hotel_id, payload_json,
    status, attempt_count, next_attempt_at, locked_at, locked_by,
    processed_at, created_at, updated_at
)
select ('40000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
       'TASK_CREATED',
       'TASK',
       ('10000000-0000-4000-8000-' || lpad(gs::text, 12, '0'))::uuid,
       ('00000000-0000-4000-8000-' || lpad((case when gs <= 10000 then 1 else ((gs - 10001) % 49) + 2 end)::text, 12, '0'))::uuid,
       jsonb_build_object('payloadVersion', 1, 'taskId', ('10000000-0000-4000-8000-' || lpad(gs::text, 12, '0')), 'hotelId', ('00000000-0000-4000-8000-' || lpad((case when gs <= 10000 then 1 else ((gs - 10001) % 49) + 2 end)::text, 12, '0')), 'createdAt', '2026-07-15T00:00:00Z'),
       case when gs % 20 = 0 then 'PENDING' when gs % 25 = 0 then 'PROCESSING' when gs % 27 = 0 then 'FAILED' else 'COMPLETED' end,
       gs % 5,
       ('2026-07-15T00:00:00Z'::timestamptz + ((gs % 5000) || ' seconds')::interval),
       case when gs % 25 = 0 then '2026-07-14T00:00:00Z'::timestamptz else null end,
       case when gs % 25 = 0 then 'fixture-processor' else null end,
       case when gs % 20 <> 0 and gs % 25 <> 0 and gs % 27 <> 0 then '2026-07-15T00:00:00Z'::timestamptz else null end,
       ('2026-07-14T00:00:00Z'::timestamptz + ((gs % 100000) || ' seconds')::interval),
       ('2026-07-14T00:00:00Z'::timestamptz + ((gs % 100000) || ' seconds')::interval)
from generate_series(1, 100000) as gs
on conflict (id) do nothing;

analyze hotel;
analyze task;
analyze notifications;
analyze operational_outbox;

select 'fixture_counts' as label,
       (select count(*) from hotel where code like 'sprint-8f-hotel-%') as hotels,
       (select count(*) from task where id::text like '10000000-0000-4000-8000-%') as tasks,
       (select count(*) from notifications where id::text like '30000000-0000-4000-8000-%') as notifications,
       (select count(*) from operational_outbox where id::text like '40000000-0000-4000-8000-%') as outbox;

\echo task_created_range
explain (analyze, buffers)
select count(*) from task
where hotel_id = '00000000-0000-4000-8000-000000000001'::uuid
  and created_at >= '2026-07-01T00:00:00Z'::timestamptz
  and created_at < '2026-07-01T06:00:00Z'::timestamptz;

\echo task_assignment_mine
explain (analyze, buffers)
select * from task
where hotel_id = '00000000-0000-4000-8000-000000000001'::uuid
  and assignee_type = 'USER'
  and assignee_id = '20000000-0000-4000-8000-000000000042'
order by updated_at desc
limit 20;

\echo active_sla_due_soon
explain (analyze, buffers)
select count(*) from task
where hotel_id = '00000000-0000-4000-8000-000000000001'::uuid
  and status not in ('COMPLETED', 'CANCELLED')
  and sla_deadline is not null
  and sla_deadline > '2026-07-15T00:00:00Z'::timestamptz
  and sla_deadline <= '2026-07-15T02:00:00Z'::timestamptz;

\echo outbox_claim
explain (analyze, buffers)
with candidates as (
    select id
    from operational_outbox
    where status = 'PENDING'
      and next_attempt_at <= '2026-07-15T00:30:00Z'::timestamptz
    order by created_at asc
    limit 25
    for update skip locked
)
select count(*) from candidates;

\echo text_search_current_semantics
explain (analyze, buffers)
select * from task
where hotel_id = '00000000-0000-4000-8000-000000000001'::uuid
  and (
    lower(title) like '%task 99%'
    or lower(description) like '%task 99%'
    or lower(room_number) like '%task 99%'
    or lower(assignee_display_name) like '%task 99%'
  )
order by updated_at desc
limit 20;

-- Optional cleanup. Uncomment only when you want to remove Sprint 8F fixture
-- rows from a disposable database.
-- delete from operational_outbox where id::text like '40000000-0000-4000-8000-%';
-- delete from notifications where id::text like '30000000-0000-4000-8000-%';
-- delete from task where id::text like '10000000-0000-4000-8000-%';
-- delete from hotel where code like 'sprint-8f-hotel-%';
