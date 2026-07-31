alter table recommendation_pilot_state
    add column schedule_paused boolean not null default false,
    add column schedule_paused_at timestamptz,
    add column schedule_resumed_at timestamptz,
    add column last_schedule_attempted_at timestamptz,
    add column last_schedule_successful_at timestamptz,
    add column last_schedule_outcome varchar(80),
    add column last_selected_candidate_count integer not null default 0,
    add column last_generated_recommendation_count integer not null default 0,
    add column last_budget_rejection_count integer not null default 0,
    add column last_schedule_failure_category varchar(120);

create index idx_recommendation_pilot_run_trigger_started
    on recommendation_pilot_run (trigger_type, started_at desc);
