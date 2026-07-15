create table assistant_attachment (
    id uuid primary key,
    conversation_id text not null,
    hotel_id text not null,
    user_id text not null,
    type text not null,
    original_file_name text not null,
    declared_mime_type text not null,
    declared_size_bytes bigint not null,
    width_px integer null,
    height_px integer null,
    storage_status text not null,
    storage_reference text null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_assistant_attachment_conversation
        foreign key (conversation_id) references assistant_conversation (id) on delete cascade,
    constraint chk_assistant_attachment_type
        check (type in ('IMAGE', 'PDF', 'DOCUMENT')),
    constraint chk_assistant_attachment_file_name
        check (length(trim(original_file_name)) between 1 and 180),
    constraint chk_assistant_attachment_mime
        check (declared_mime_type in ('image/jpeg', 'image/png', 'image/webp', 'application/pdf', 'text/plain')),
    constraint chk_assistant_attachment_size
        check (declared_size_bytes between 1 and 10000000),
    constraint chk_assistant_attachment_dimensions
        check (
            (
                type = 'IMAGE'
                and (width_px is null or width_px between 1 and 10000)
                and (height_px is null or height_px between 1 and 10000)
            )
            or (
                type <> 'IMAGE'
                and width_px is null
                and height_px is null
            )
        ),
    constraint chk_assistant_attachment_storage_status
        check (storage_status = 'REGISTERED'),
    constraint chk_assistant_attachment_storage_reference
        check (storage_reference is null)
);

create index idx_assistant_attachment_conversation_created_at
    on assistant_attachment (conversation_id, created_at);

create index idx_assistant_attachment_scope
    on assistant_attachment (hotel_id, user_id, conversation_id);
