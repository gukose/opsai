alter table reservation_task_recommendation
    add column decision_reason varchar(80),
    add column decision_note varchar(500);

alter table reservation_task_recommendation
    add constraint chk_reservation_task_recommendation_decision_reason check (
        decision_reason is null or decision_reason in (
            'OPERATIONALLY_RELEVANT',
            'DUPLICATE_WORK',
            'LOW_CONFIDENCE',
            'INCORRECT_CATEGORY',
            'NOT_ACTIONABLE',
            'OUTDATED',
            'OTHER'
        )
    );
