alter table task_interruption
    add column source text not null default 'MANUAL';

alter table task_interruption
    add constraint ck_task_interruption_source
        check (source in ('MANUAL', 'FLASH_INTERRUPTION'));
