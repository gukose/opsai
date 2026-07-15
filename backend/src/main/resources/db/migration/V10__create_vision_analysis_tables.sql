create table vision_analysis (
    id uuid primary key,
    attachment_id uuid not null,
    conversation_id text not null,
    hotel_id text not null,
    user_id text not null,
    status text not null,
    provider_id text not null,
    provider_model text null,
    provider_version text null,
    confidence numeric null,
    observations_json jsonb not null default '[]'::jsonb,
    detected_issue_category text null,
    detected_location_hint text null,
    provider_metadata_json jsonb not null default '{}'::jsonb,
    failure_code text null,
    failure_message text null,
    idempotency_key text not null,
    attempt_count integer not null,
    requested_at timestamptz not null,
    completed_at timestamptz null,
    failed_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_vision_analysis_attachment
        foreign key (attachment_id) references assistant_attachment (id) on delete cascade,
    constraint chk_vision_analysis_status
        check (status in ('PENDING', 'COMPLETED', 'FAILED', 'INELIGIBLE')),
    constraint chk_vision_analysis_provider_id
        check (length(trim(provider_id)) between 1 and 120),
    constraint chk_vision_analysis_confidence
        check (confidence is null or (confidence >= 0 and confidence <= 1)),
    constraint chk_vision_analysis_attempt_count
        check (attempt_count >= 1),
    constraint chk_vision_analysis_completed_at
        check ((status = 'COMPLETED' and completed_at is not null and failed_at is null) or status <> 'COMPLETED'),
    constraint chk_vision_analysis_failed_at
        check ((status in ('FAILED', 'INELIGIBLE') and failed_at is not null and completed_at is null) or status not in ('FAILED', 'INELIGIBLE'))
);

create unique index uq_vision_analysis_idempotency_scope
    on vision_analysis (attachment_id, conversation_id, hotel_id, user_id, idempotency_key);

create index idx_vision_analysis_scope
    on vision_analysis (hotel_id, user_id, conversation_id);

create index idx_vision_analysis_attachment
    on vision_analysis (attachment_id, requested_at desc);
