alter table assistant_attachment
    drop constraint if exists chk_assistant_attachment_storage_reference;

alter table assistant_attachment
    add constraint chk_assistant_attachment_storage_reference
    check (storage_reference is null or length(trim(storage_reference)) between 1 and 1024);
