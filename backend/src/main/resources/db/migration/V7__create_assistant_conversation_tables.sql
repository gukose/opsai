create table assistant_conversation (
    id text primary key,
    hotel_id text not null,
    user_id text not null,
    state text not null,
    intent text not null,
    collected_fields_json jsonb not null default '{}'::jsonb,
    missing_fields_json jsonb not null default '[]'::jsonb,
    follow_up_question_json jsonb null,
    task_preview_json jsonb null,
    messages_json jsonb not null default '[]'::jsonb,
    active_draft_id text null,
    draft_version integer not null default 0,
    created_task_id text null,
    confirmation_idempotency_key text null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_assistant_conversation_hotel_id on assistant_conversation (hotel_id);
create index idx_assistant_conversation_user_id on assistant_conversation (user_id);
create index idx_assistant_conversation_state on assistant_conversation (state);
create index idx_assistant_conversation_updated_at on assistant_conversation (updated_at desc);

create table assistant_task_confirmation (
    id uuid primary key,
    conversation_id text not null,
    idempotency_key text not null,
    created_task_id text not null,
    draft_id text not null,
    draft_version integer not null,
    preview_json jsonb not null,
    created_at timestamptz not null,
    constraint fk_assistant_task_confirmation_conversation
        foreign key (conversation_id) references assistant_conversation (id) on delete cascade,
    constraint uq_assistant_task_confirmation_idempotency
        unique (conversation_id, idempotency_key)
);

create index idx_assistant_task_confirmation_conversation_id
    on assistant_task_confirmation (conversation_id);
create index idx_assistant_task_confirmation_created_task_id
    on assistant_task_confirmation (created_task_id);
create index idx_assistant_task_confirmation_created_at
    on assistant_task_confirmation (created_at);
