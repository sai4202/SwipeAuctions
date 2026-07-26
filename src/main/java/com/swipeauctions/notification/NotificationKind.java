package com.swipeauctions.notification;

/** Kinds of real-time push notifications delivered to a signed-in user over /user/queue/notifications. */
public enum NotificationKind {
    BID_PLACED,
    OUTBID,
    AUCTION_WON,
    AUCTION_LOST,
    WALLET_TOPUP
}
