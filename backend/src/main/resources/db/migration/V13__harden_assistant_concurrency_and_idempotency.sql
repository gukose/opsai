alter table assistant_conversation
    add column row_version bigint not null default 0;

alter table assistant_task_confirmation
    add constraint uq_assistant_task_confirmation_draft
        unique (conversation_id, draft_id, draft_version);

alter table assistant_attachment
    add column registration_idempotency_key text null;

create unique index uq_assistant_attachment_registration_idempotency
    on assistant_attachment (hotel_id, user_id, conversation_id, registration_idempotency_key)
    where registration_idempotency_key is not null;
