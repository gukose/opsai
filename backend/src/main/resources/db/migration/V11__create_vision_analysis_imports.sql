create table vision_analysis_import (
    id uuid primary key,
    analysis_id uuid not null,
    conversation_id text not null,
    attachment_id uuid not null,
    hotel_id text not null,
    user_id text not null,
    message_id text null,
    status text not null,
    failure_code text null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_vision_analysis_import_analysis
        foreign key (analysis_id) references vision_analysis (id) on delete cascade,
    constraint fk_vision_analysis_import_attachment
        foreign key (attachment_id) references assistant_attachment (id) on delete cascade,
    constraint fk_vision_analysis_import_conversation
        foreign key (conversation_id) references assistant_conversation (id) on delete cascade,
    constraint chk_vision_analysis_import_status
        check (status in ('PENDING', 'COMPLETED', 'FAILED')),
    constraint chk_vision_analysis_import_completed_message
        check ((status = 'COMPLETED' and message_id is not null and failure_code is null) or status <> 'COMPLETED')
);

create unique index uq_vision_analysis_import_conversation_analysis
    on vision_analysis_import (conversation_id, analysis_id);

create index idx_vision_analysis_import_scope
    on vision_analysis_import (hotel_id, user_id, conversation_id);

create index idx_vision_analysis_import_analysis
    on vision_analysis_import (analysis_id);
