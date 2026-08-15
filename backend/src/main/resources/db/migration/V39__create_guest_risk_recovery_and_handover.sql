create table guest_session (
    id uuid primary key, hotel_id uuid not null references hotel(id), room_number text not null, stay_reference_hash text null,
    token_hash text not null unique, expires_at timestamptz not null, revoked_at timestamptz null, created_at timestamptz not null,
    last_used_at timestamptz null, rate_limit_window_start timestamptz null, rate_limit_count integer not null default 0
);
create index idx_guest_session_scope on guest_session(hotel_id, room_number, expires_at);
create table guest_message (
    id uuid primary key, hotel_id uuid not null references hotel(id), guest_session_id uuid not null references guest_session(id),
    direction text not null, provider text not null, provider_message_key_hash text not null,
    safe_message_category text not null, normalized_text text null, delivery_status text not null,
    task_id uuid null references task(id), created_at timestamptz not null, updated_at timestamptz not null,
    unique(provider, provider_message_key_hash)
);
create table satisfaction_survey (
    id uuid primary key, hotel_id uuid not null references hotel(id), guest_session_id uuid not null references guest_session(id),
    business_date date not null, status text not null, score integer null check(score between 1 and 5), category text null,
    idempotency_key text not null, sent_at timestamptz null, responded_at timestamptz null, created_at timestamptz not null,
    unique(hotel_id, idempotency_key)
);
create table guest_risk_assessment (
    id uuid primary key, hotel_id uuid not null references hotel(id), guest_session_id uuid not null references guest_session(id),
    level text not null, confidence numeric(5,4) not null, rule_version text not null, reason_codes text[] not null,
    overridden_level text null, overridden_by uuid null references app_user(id), assessed_at timestamptz not null
);
create table service_recovery (
    id uuid primary key, hotel_id uuid not null references hotel(id), origin_type text not null, origin_id uuid not null,
    reason_code text not null, status text not null, assigned_department_id uuid null references department(id), assigned_user_id uuid null references app_user(id),
    recommended_action text null, compensation_required boolean not null default false, compensation_status text null,
    approved_by uuid null references app_user(id), outcome text null, follow_up_status text not null,
    created_at timestamptz not null, completed_at timestamptz null, idempotency_key text not null,
    unique(hotel_id, idempotency_key)
);
create table shift_handover (
    id uuid primary key, hotel_id uuid not null references hotel(id), author_user_id uuid not null references app_user(id),
    target_department_id uuid not null references department(id), room_number text null, tags text[] not null default '{}',
    note text not null, importance text not null, required_read boolean not null, task_id uuid null references task(id),
    created_at timestamptz not null
);
create table shift_handover_ack (handover_id uuid not null references shift_handover(id) on delete cascade, user_id uuid not null references app_user(id), acknowledged_at timestamptz not null, primary key(handover_id, user_id));
