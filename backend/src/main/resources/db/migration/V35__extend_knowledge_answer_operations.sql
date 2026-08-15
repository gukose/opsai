alter table knowledge_answer_history
    add column if not exists actor_user_id uuid;

create index if not exists idx_knowledge_answer_history_actor_created
    on knowledge_answer_history(hotel_id, actor_user_id, created_at desc);

create table knowledge_answer_feedback (
    answer_id uuid not null references knowledge_answer_history(id) on delete cascade,
    feedback_type varchar(80) not null,
    actor_user_id uuid,
    created_at timestamptz not null,
    primary key (answer_id, actor_user_id, feedback_type)
);

create index idx_knowledge_answer_feedback_created
    on knowledge_answer_feedback(created_at desc);

create table knowledge_answer_provider_diagnostic (
    id uuid primary key,
    provider_id varchar(80) not null,
    diagnostic_type varchar(80) not null,
    trigger_type varchar(80) not null,
    started_at timestamptz not null,
    completed_at timestamptz not null,
    outcome varchar(80) not null,
    failure_category varchar(120),
    latency_band varchar(40) not null,
    retry_count integer not null default 0,
    response_validation_outcome varchar(80) not null,
    prompt_template_id varchar(160) not null,
    prompt_version varchar(160) not null,
    model_id varchar(160) not null,
    environment_class varchar(80) not null,
    created_at timestamptz not null
);

create index idx_knowledge_answer_provider_diagnostic_provider_created
    on knowledge_answer_provider_diagnostic(provider_id, created_at desc);

create index idx_knowledge_answer_provider_diagnostic_outcome
    on knowledge_answer_provider_diagnostic(outcome, created_at desc);
