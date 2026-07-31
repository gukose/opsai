alter table reservation_task_recommendation
    add constraint chk_reservation_task_recommendation_source check (
        source in ('INTERNAL_DEMO_AI', 'EXTERNAL_LLM')
    );
