-- Admin-managed membership benefit list shown under every tier card, replacing the previously
-- hardcoded BASE_FEATURES/unlock-string logic in TierCards.tsx. Seeded here with today's effective
-- content (same 4 base features + 3 cumulative tier-access unlocks) so the deploy that ships this
-- migration doesn't blank the membership section — admin edits from here on via the Settings tab.

create table membership_benefits (
    id uuid not null,
    name varchar(200) not null,
    sort_order integer not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id)
);

create table membership_benefit_tiers (
    benefit_id uuid not null,
    tier varchar(20) not null check (tier in ('SILVER','GOLD','DIAMOND')),
    primary key (benefit_id, tier),
    foreign key (benefit_id) references membership_benefits (id) on delete cascade
);

insert into membership_benefits (id, name, sort_order, created_at, updated_at) values
    ('9c1f0000-0000-4000-8000-000000000001', 'Browse & bid on standard listings', 0, now(), now()),
    ('9c1f0000-0000-4000-8000-000000000002', 'Real-time live bidding', 1, now(), now()),
    ('9c1f0000-0000-4000-8000-000000000003', 'No per-item deposits — bid freely within your wallet credit limit', 2, now(), now()),
    ('9c1f0000-0000-4000-8000-000000000004', 'Leveraged wallet credit limit on every deposit', 3, now(), now()),
    ('9c1f0000-0000-4000-8000-000000000005', 'Access to Silver-tier listings', 4, now(), now()),
    ('9c1f0000-0000-4000-8000-000000000006', 'Access to Gold-tier listings', 5, now(), now()),
    ('9c1f0000-0000-4000-8000-000000000007', 'Access to Diamond-tier listings', 6, now(), now());

insert into membership_benefit_tiers (benefit_id, tier) values
    ('9c1f0000-0000-4000-8000-000000000001','SILVER'), ('9c1f0000-0000-4000-8000-000000000001','GOLD'), ('9c1f0000-0000-4000-8000-000000000001','DIAMOND'),
    ('9c1f0000-0000-4000-8000-000000000002','SILVER'), ('9c1f0000-0000-4000-8000-000000000002','GOLD'), ('9c1f0000-0000-4000-8000-000000000002','DIAMOND'),
    ('9c1f0000-0000-4000-8000-000000000003','SILVER'), ('9c1f0000-0000-4000-8000-000000000003','GOLD'), ('9c1f0000-0000-4000-8000-000000000003','DIAMOND'),
    ('9c1f0000-0000-4000-8000-000000000004','SILVER'), ('9c1f0000-0000-4000-8000-000000000004','GOLD'), ('9c1f0000-0000-4000-8000-000000000004','DIAMOND'),
    ('9c1f0000-0000-4000-8000-000000000005','SILVER'), ('9c1f0000-0000-4000-8000-000000000005','GOLD'), ('9c1f0000-0000-4000-8000-000000000005','DIAMOND'),
    ('9c1f0000-0000-4000-8000-000000000006','GOLD'), ('9c1f0000-0000-4000-8000-000000000006','DIAMOND'),
    ('9c1f0000-0000-4000-8000-000000000007','DIAMOND');
