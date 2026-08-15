create table inventory_category (id uuid primary key, hotel_id uuid not null references hotel(id), code text not null, name text not null, unique(hotel_id, code));
create table inventory_location (id uuid primary key, hotel_id uuid not null references hotel(id), code text not null, name text not null, location_type text not null, active boolean not null default true, unique(hotel_id, code));
create table inventory_item (
    id uuid primary key, hotel_id uuid not null references hotel(id), category_id uuid null references inventory_category(id),
    code text not null, name text not null, unit text not null, unit_price numeric(14,2) null check(unit_price is null or unit_price >= 0),
    negative_stock_allowed boolean not null default false, active boolean not null default true,
    created_at timestamptz not null, updated_at timestamptz not null, unique(hotel_id, code)
);
create table inventory_balance (
    hotel_id uuid not null references hotel(id), item_id uuid not null references inventory_item(id), location_id uuid not null references inventory_location(id),
    quantity numeric(16,3) not null default 0, version bigint not null default 0, updated_at timestamptz not null,
    primary key(hotel_id, item_id, location_id)
);
create table inventory_transaction (
    id uuid primary key, hotel_id uuid not null references hotel(id), item_id uuid not null references inventory_item(id),
    from_location_id uuid null references inventory_location(id), to_location_id uuid null references inventory_location(id),
    transaction_type text not null, quantity numeric(16,3) not null check(quantity > 0), unit_price numeric(14,2) null,
    operational_reference text not null, note text null, actor_user_id uuid null references app_user(id), occurred_at timestamptz not null,
    created_at timestamptz not null, unique(hotel_id, operational_reference, item_id, transaction_type)
);
create index idx_inventory_tx_hotel_time on inventory_transaction(hotel_id, occurred_at desc);

create table minibar_inspection (
    id uuid primary key, hotel_id uuid not null references hotel(id), task_id uuid not null references task(id), room_number text not null,
    result text not null, completed_at timestamptz null, idempotency_key text not null, created_at timestamptz not null,
    unique(hotel_id, task_id), unique(hotel_id, idempotency_key)
);
create table minibar_inspection_item (
    inspection_id uuid not null references minibar_inspection(id) on delete cascade, inventory_item_id uuid not null references inventory_item(id),
    quantity numeric(16,3) not null check(quantity > 0), source text not null, primary key(inspection_id, inventory_item_id)
);
create table financial_charge_proposal (
    id uuid primary key, hotel_id uuid not null references hotel(id), charge_type text not null, source_id uuid not null,
    room_number text not null, amount numeric(14,2) not null check(amount >= 0), currency char(3) not null,
    status text not null, idempotency_key text not null, requested_at timestamptz not null,
    reviewed_by uuid null references app_user(id), reviewed_at timestamptz null, rejection_reason text null,
    provider_id text null, provider_reference text null, posted_at timestamptz null, failure_category text null,
    unique(hotel_id, idempotency_key), unique(hotel_id, charge_type, source_id)
);

create table damage_report (
    id uuid primary key, hotel_id uuid not null references hotel(id), task_id uuid null references task(id), room_number text null,
    location text not null, category text not null, description text not null, status text not null,
    suggested_amount numeric(14,2) null, approved_amount numeric(14,2) null, currency char(3) null,
    vision_analysis_id uuid null, ai_provider text null, ai_confidence numeric(5,4) null,
    created_by uuid not null references app_user(id), reviewed_by uuid null references app_user(id),
    created_at timestamptz not null, reviewed_at timestamptz null, closed_at timestamptz null,
    idempotency_key text not null, unique(hotel_id, idempotency_key)
);
create table damage_attachment (damage_report_id uuid not null references damage_report(id) on delete cascade, attachment_id uuid not null, provenance text not null, primary key(damage_report_id, attachment_id));
