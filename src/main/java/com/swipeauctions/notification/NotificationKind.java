package com.swipeauctions.notification;

/** Kinds of real-time push notifications delivered to a signed-in user over /user/queue/notifications. */
public enum NotificationKind {
    BID_PLACED,
    OUTBID,
    AUCTION_WON,
    AUCTION_LOST,
    WALLET_TOPUP,
    REFERRAL_BONUS,
    WALLET_ADJUSTED,
    REGISTRATION_FEE_PAID,
    SUBSCRIPTION_ACTIVATED,
    PAYMENT_FAILED,
    SETTLEMENT_COMPLETED,
    SETTLEMENT_REMINDER,
    WIN_APPROVED,
    WIN_REJECTED
}
