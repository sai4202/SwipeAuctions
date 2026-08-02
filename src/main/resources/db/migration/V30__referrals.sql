-- V30 — Referrals: tracks who referred whom at signup (captured client-side via a "?ref=<user id>"
-- link, see ReferralController). No column added to `users` — the referral "code" is simply the
-- referrer's own id, so this migration only adds a new, fully independent table. Purely additive:
-- doesn't touch registration, wallet, or auth logic. DDL generated from the Referral entity so
-- ddl-auto=validate matches.

create table referrals (
    id uuid not null,
    referrer_id uuid not null,
    referred_id uuid not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    unique (referred_id)
);

create index idx_referrals_referrer on referrals (referrer_id);

alter table if exists referrals
    add constraint fk_referrals_referrer foreign key (referrer_id) references users;

alter table if exists referrals
    add constraint fk_referrals_referred foreign key (referred_id) references users;
