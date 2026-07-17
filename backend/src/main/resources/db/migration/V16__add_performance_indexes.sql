create index idx_task_hotel_created_at
    on task (hotel_id, created_at);

create index idx_task_hotel_assignee_updated_at_desc
    on task (hotel_id, assignee_type, assignee_id, updated_at desc);

create index idx_task_hotel_active_sla_deadline
    on task (hotel_id, sla_deadline)
    where status not in ('COMPLETED', 'CANCELLED');

create index idx_operational_outbox_pending_created_due
    on operational_outbox (created_at, next_attempt_at)
    where status = 'PENDING';
