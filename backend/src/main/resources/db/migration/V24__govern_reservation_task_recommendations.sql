alter table reservation_task_recommendation
    add column context_schema_version text not null default 'reservation-task-recommendation-context-v1';

create table reservation_task_recommendation_generation_run (
    id uuid primary key,
    trigger_type text not null,
    provider_id text not null,
    status text not null,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    candidates_selected integer not null default 0,
    candidates_processed integer not null default 0,
    recommendations_generated integer not null default 0,
    duplicates_prevented integer not null default 0,
    skipped_count integer not null default 0,
    failed_count integer not null default 0,
    failure_category text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint chk_task_recommendation_run_trigger check (
        trigger_type in ('OPERATOR', 'SCHEDULED')
    ),
    constraint chk_task_recommendation_run_status check (
        status in ('REQUESTED', 'RUNNING', 'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'REJECTED')
    ),
    constraint chk_task_recommendation_run_counts check (
        candidates_selected >= 0 and
        candidates_processed >= 0 and
        recommendations_generated >= 0 and
        duplicates_prevented >= 0 and
        skipped_count >= 0 and
        failed_count >= 0
    ),
    constraint chk_task_recommendation_run_version check (version >= 0)
);

create index idx_task_recommendation_generation_run_status
    on reservation_task_recommendation_generation_run (status, started_at desc);

create index idx_task_recommendation_generation_run_trigger
    on reservation_task_recommendation_generation_run (trigger_type, started_at desc);

create table reservation_task_recommendation_schedule_state (
    schedule_id text primary key,
    paused boolean not null default false,
    paused_at timestamp with time zone,
    resumed_at timestamp with time zone,
    last_attempted_at timestamp with time zone,
    last_successful_at timestamp with time zone,
    last_processed_candidate_count integer not null default 0,
    last_generated_recommendation_count integer not null default 0,
    last_failure_category text,
    updated_at timestamp with time zone not null,
    constraint chk_task_recommendation_schedule_counts check (
        last_processed_candidate_count >= 0 and
        last_generated_recommendation_count >= 0
    )
);
