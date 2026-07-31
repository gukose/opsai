alter table reservation_task_recommendation
    add column pilot_run_id uuid;

create table recommendation_pilot_run (
    id uuid primary key,
    provider_id varchar(120) not null,
    trigger_type varchar(80) not null,
    status varchar(80) not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    candidates_selected integer not null default 0,
    candidates_processed integer not null default 0,
    provider_calls integer not null default 0,
    recommendations_generated integer not null default 0,
    duplicates_prevented integer not null default 0,
    skipped_count integer not null default 0,
    failed_count integer not null default 0,
    request_budget_used integer not null default 0,
    recommendation_budget_used integer not null default 0,
    token_budget_used bigint not null default 0,
    model_identifier varchar(160),
    prompt_version varchar(160) not null,
    context_schema_version varchar(160) not null,
    failure_category varchar(120),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);

create index idx_recommendation_pilot_run_provider_started
    on recommendation_pilot_run (provider_id, started_at desc);

create index idx_recommendation_pilot_run_status_started
    on recommendation_pilot_run (status, started_at desc);

create table recommendation_pilot_budget_daily (
    provider_id varchar(120) not null,
    budget_date date not null,
    request_count integer not null default 0,
    recommendation_count integer not null default 0,
    token_count bigint not null default 0,
    updated_at timestamptz not null,
    primary key (provider_id, budget_date)
);

create table recommendation_pilot_state (
    state_id varchar(120) primary key,
    disabled boolean not null default false,
    disabled_at timestamptz,
    last_rollback_at timestamptz,
    updated_at timestamptz not null
);
