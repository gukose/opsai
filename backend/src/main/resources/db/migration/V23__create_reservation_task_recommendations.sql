create table reservation_task_recommendation (
    id uuid primary key,
    reservation_id uuid not null,
    source text not null,
    provider_name text not null,
    model_identifier text,
    prompt_version text not null,
    category text not null,
    confidence text not null,
    explanation_situation text not null,
    explanation_rationale text not null,
    explanation_signals text not null,
    task_intent_type text not null,
    task_title text not null,
    task_description text not null,
    task_priority text not null,
    task_due_at timestamp with time zone not null,
    deduplication_key text not null,
    status text not null,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    applied_task_id uuid references task(id) on delete set null,
    attempt_count integer not null default 0,
    next_attempt_at timestamp with time zone,
    failure_category text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    version bigint not null default 0,
    constraint uq_reservation_task_recommendation_dedupe unique (deduplication_key),
    constraint chk_reservation_task_recommendation_status check (
        status in ('GENERATED', 'REVIEW_REQUIRED', 'APPROVED', 'REJECTED', 'EXPIRED', 'APPLIED', 'FAILED')
    ),
    constraint chk_reservation_task_recommendation_confidence check (
        confidence in ('LOW', 'MEDIUM', 'HIGH')
    ),
    constraint chk_reservation_task_recommendation_attempts check (attempt_count >= 0),
    constraint chk_reservation_task_recommendation_version check (version >= 0)
);

create index idx_reservation_task_recommendation_status
    on reservation_task_recommendation (status, next_attempt_at, created_at);

create index idx_reservation_task_recommendation_reservation
    on reservation_task_recommendation (reservation_id, created_at desc);
