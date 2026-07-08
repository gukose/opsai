create table task (
    id uuid primary key,
    task_key text not null unique,
    hotel_id text not null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    intent_type text not null,
    source text not null,
    title text not null,
    description text not null,
    priority text not null,
    status text not null,
    sla_deadline timestamptz not null,
    assignee_type text null,
    assignee_id text null,
    assignee_display_name text null,
    assigned_at timestamptz null,
    started_at timestamptz null,
    completed_at timestamptz null,
    cancelled_at timestamptz null,
    overdue_at timestamptz null
);

create index idx_task_hotel_id on task (hotel_id);
create index idx_task_status on task (status);
create index idx_task_sla_deadline on task (sla_deadline);
create index idx_task_assignee_id on task (assignee_id);
create index idx_task_updated_at on task (updated_at desc);

create table task_state_history (
    id uuid primary key,
    task_id text not null,
    hotel_id text not null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    from_status text null,
    to_status text not null,
    operation text not null,
    note text null,
    correlation_id text null
);

create index idx_task_state_history_task_id on task_state_history (task_id);
create index idx_task_state_history_hotel_id on task_state_history (hotel_id);
create index idx_task_state_history_created_at on task_state_history (created_at);

create table task_log (
    id uuid primary key,
    task_id text not null,
    hotel_id text not null,
    version bigint not null,
    created_at timestamptz not null,
    created_by text null,
    updated_at timestamptz not null,
    updated_by text null,
    operation text not null,
    outcome text not null,
    message text not null,
    from_status text null,
    to_status text null,
    correlation_id text null
);

create index idx_task_log_task_id on task_log (task_id);
create index idx_task_log_hotel_id on task_log (hotel_id);
create index idx_task_log_created_at on task_log (created_at);
