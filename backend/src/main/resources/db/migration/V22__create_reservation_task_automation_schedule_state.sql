create table reservation_task_automation_schedule_state (
    schedule_id text primary key,
    paused boolean not null default false,
    paused_at timestamp with time zone,
    resumed_at timestamp with time zone,
    last_attempted_at timestamp with time zone,
    last_successful_at timestamp with time zone,
    last_processed_count integer not null default 0,
    last_created_task_count integer not null default 0,
    last_failure_category text,
    updated_at timestamp with time zone not null,
    constraint chk_reservation_task_automation_schedule_counts check (
        last_processed_count >= 0 and last_created_task_count >= 0
    )
);
