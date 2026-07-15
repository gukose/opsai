alter table assistant_conversation
    add column active_draft_source_message_ids_json jsonb not null default '[]'::jsonb;

create table task_attachment_link (
    id uuid primary key,
    task_id uuid not null,
    attachment_id uuid not null,
    conversation_id text not null,
    hotel_id text not null,
    user_id text not null,
    source_type text not null,
    analysis_id uuid null,
    analysis_import_id uuid null,
    created_at timestamptz not null,
    constraint fk_task_attachment_link_task
        foreign key (task_id) references task (id) on delete cascade,
    constraint fk_task_attachment_link_attachment
        foreign key (attachment_id) references assistant_attachment (id),
    constraint fk_task_attachment_link_conversation
        foreign key (conversation_id) references assistant_conversation (id),
    constraint fk_task_attachment_link_analysis
        foreign key (analysis_id) references vision_analysis (id),
    constraint fk_task_attachment_link_analysis_import
        foreign key (analysis_import_id) references vision_analysis_import (id),
    constraint chk_task_attachment_link_source
        check (source_type in ('ASSISTANT_MESSAGE', 'VISION_ANALYSIS')),
    constraint chk_task_attachment_link_provenance
        check (
            (
                source_type = 'ASSISTANT_MESSAGE'
                and analysis_id is null
                and analysis_import_id is null
            )
            or (
                source_type = 'VISION_ANALYSIS'
                and analysis_id is not null
            )
        )
);

create unique index uq_task_attachment_link_task_attachment
    on task_attachment_link (task_id, attachment_id);

create index idx_task_attachment_link_task
    on task_attachment_link (task_id);

create index idx_task_attachment_link_scope
    on task_attachment_link (hotel_id, user_id, conversation_id);

create index idx_task_attachment_link_conversation
    on task_attachment_link (conversation_id);

create index idx_task_attachment_link_attachment
    on task_attachment_link (attachment_id);
