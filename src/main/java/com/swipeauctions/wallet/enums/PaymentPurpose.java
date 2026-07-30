package com.swipeauctions.wallet.enums;

/** What a {@code PaymentOrder} pays for — decides what {@code RazorpayPaymentService.settle}
 *  does once the payment is confirmed. */
public enum PaymentPurpose {
    WALLET_TOPUP,
    REGISTRATION_FEE,
    SUBSCRIPTION
}
