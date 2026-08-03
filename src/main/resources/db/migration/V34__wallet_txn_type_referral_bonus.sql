-- Adds REFERRAL_BONUS to wallet_transactions' type check constraint (V8) for the new
-- admin-configurable referral bonus feature (see ReferralService#onTopUp).

alter table wallet_transactions
    drop constraint wallet_transactions_type_check;
alter table wallet_transactions
    add constraint wallet_transactions_type_check
        check ((type in ('TOPUP','HOLD','RELEASE','CAPTURE','DEBIT','PAYOUT','WITHDRAWAL','REFUND','SALE_PROCEEDS','REFERRAL_BONUS')));
