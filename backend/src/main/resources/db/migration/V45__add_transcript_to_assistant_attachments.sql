alter table assistant_attachment
    add column transcript_text text null;

alter table assistant_attachment
    add constraint chk_assistant_attachment_transcript_size
    check (transcript_text is null or length(transcript_text) between 1 and 4000);
