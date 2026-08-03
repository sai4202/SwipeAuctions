-- Turns referrals from tracking-only into a real reward: a referral becomes SUCCESSFUL once the
-- referred user makes a single top-up meeting the admin-configured minimum deposit, at which point
-- the admin-configured bonus is credited to the referrer's wallet (see ReferralService#onTopUp).
-- reward_amount/credited_at are only ever set once, at that moment.

alter table referrals add column status varchar(20) not null default 'PENDING';
alter table referrals add column reward_amount numeric(12,2);
alter table referrals add column credited_at timestamp(6);

alter table platform_settings add column referral_bonus_amount numeric(12,2) not null default 0;
alter table platform_settings add column referral_min_deposit numeric(12,2) not null default 5000;
