-- Replace Stripe with Razorpay across the whole payment surface: wallet top-up, seller payouts,
-- and (newly) real payment for registration fee / subscription purchase, which were previously
-- unpaid stubs. wallet_topups becomes the generic payment_orders table, reused by all three buyer
-- flows via `purpose` (see PaymentPurpose) — a top-up credits the wallet, a registration-fee/
-- subscription order flips the matching flag/tier once Razorpay confirms payment. No real Stripe
-- transaction history exists in this DB (STRIPE_* was never set locally), so this is a clean rename,
-- not a data-preserving migration.

-- ---- payment_orders (was wallet_topups) ----
alter table wallet_topups rename to payment_orders;
alter table payment_orders rename column stripe_payment_intent_id to razorpay_order_id;
alter table payment_orders add column purpose varchar(30) not null default 'WALLET_TOPUP';
alter table payment_orders add column metadata varchar(100);

alter table payment_orders add column user_id uuid;
update payment_orders po set user_id = w.user_id from wallets w where w.id = po.wallet_id;
alter table payment_orders alter column user_id set not null;
alter table payment_orders add constraint fk_payment_order_user foreign key (user_id) references users;
create index idx_payment_orders_user on payment_orders (user_id);

alter table payment_orders drop constraint if exists fk_wallet_topup_wallet;
drop index if exists idx_wallet_topups_wallet;
alter table payment_orders drop column wallet_id;

-- ---- wallet_withdrawals ----
alter table wallet_withdrawals rename column stripe_transfer_id to razorpay_payout_id;

-- ---- users: Razorpay payout identity (Contact + Fund Account, no OAuth-redirect "Connect" flow) ----
alter table users drop column if exists stripe_customer_id;
alter table users drop column if exists stripe_charges_enabled;
alter table users rename column stripe_account_id to razorpay_contact_id;
alter table users add column razorpay_fund_account_id varchar(64);
alter table users rename column stripe_payouts_enabled to razorpay_payouts_enabled;
