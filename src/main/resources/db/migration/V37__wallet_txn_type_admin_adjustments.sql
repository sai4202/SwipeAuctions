-- Adds ADMIN_CREDIT/ADMIN_DEBIT to wallet_transactions' type check constraint (V8/V34) for the new
-- admin manual wallet-adjustment tool (see WalletService#adminAdjust).

alter table wallet_transactions
    drop constraint wallet_transactions_type_check;
alter table wallet_transactions
    add constraint wallet_transactions_type_check
        check ((type in ('TOPUP','HOLD','RELEASE','CAPTURE','DEBIT','PAYOUT','WITHDRAWAL','REFUND','SALE_PROCEEDS','REFERRAL_BONUS','ADMIN_CREDIT','ADMIN_DEBIT')));
