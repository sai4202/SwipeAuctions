-- Registration fee (one-time, admin-configurable) and tiered subscriptions (Silver/Gold/Diamond
-- x monthly/quarterly/half-yearly/yearly). Neither is wired to real payment yet — see
-- SubscriptionService/PlatformSettingsService javadoc, same "stub until Phase 2 Stripe" pattern
-- already used by WalletService.topUp. Existing users/listings default to no tier/no gate, so
-- nothing already in the DB is affected.

create table platform_settings (
    id uuid not null,
    registration_fee numeric(12,2) not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id)
);

create table subscription_plan_prices (
    id uuid not null,
    tier varchar(20) not null check (tier in ('SILVER','GOLD','DIAMOND')),
    billing_cycle varchar(20) not null check (billing_cycle in ('MONTHLY','QUARTERLY','HALF_YEARLY','YEARLY')),
    price numeric(12,2) not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    unique (tier, billing_cycle)
);

insert into platform_settings (id, registration_fee, created_at, updated_at)
values (gen_random_uuid(), 0, now(), now());

insert into subscription_plan_prices (id, tier, billing_cycle, price, created_at, updated_at)
select gen_random_uuid(), t, c, 0, now(), now()
from (values ('SILVER'), ('GOLD'), ('DIAMOND')) as tiers(t)
cross join (values ('MONTHLY'), ('QUARTERLY'), ('HALF_YEARLY'), ('YEARLY')) as cycles(c);

alter table users add column subscription_tier varchar(20) not null default 'NONE'
    check (subscription_tier in ('NONE','SILVER','GOLD','DIAMOND'));
alter table users add column subscription_expires_at timestamp(6);

alter table listings add column required_tier varchar(20) not null default 'NONE'
    check (required_tier in ('NONE','SILVER','GOLD','DIAMOND'));
create index idx_listings_required_tier on listings (required_tier);
