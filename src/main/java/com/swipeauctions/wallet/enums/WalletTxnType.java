package com.swipeauctions.wallet.enums;

/** Type of an append-only wallet ledger entry. */
public enum WalletTxnType {
    TOPUP,
    HOLD,
    RELEASE,
    CAPTURE,
    DEBIT,
    PAYOUT,
    WITHDRAWAL,
    REFUND,
    SALE_PROCEEDS
}
