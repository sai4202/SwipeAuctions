package com.swipeauctions.payment;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Razorpay credentials. {@code keyId}/{@code keySecret} are blank by default (no env var set) —
 * Razorpay-backed endpoints then reject with a clear "not configured" error instead of the app
 * failing to boot, so the rest of the platform works without Razorpay keys present. Payouts need
 * the separate RazorpayX {@code accountNumber} on top of the standard keys — gated independently
 * via {@link #payoutsEnabled()} since a merchant can have Payments access without Payouts access.
 */
@Component
@Slf4j
public class RazorpayConfig {

    @Getter
    @Value("${razorpay.key-id:}")
    private String keyId;

    @Getter
    @Value("${razorpay.key-secret:}")
    private String keySecret;

    @Getter
    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    /** RazorpayX virtual account payouts are made from — required for seller withdrawals only. */
    @Getter
    @Value("${razorpay.account-number:}")
    private String accountNumber;

    @jakarta.annotation.PostConstruct
    void init() {
        if (!isEnabled()) {
            log.warn("[razorpay] RAZORPAY_KEY_ID/RAZORPAY_KEY_SECRET not set — wallet top-up, "
                    + "registration fee, and subscription payment are disabled until configured. "
                    + "Rest of the app is unaffected.");
            return;
        }
        log.info("[razorpay] configured ({} mode)", keyId.startsWith("rzp_live_") ? "LIVE" : "test");
        if (!payoutsEnabled()) {
            log.warn("[razorpay] RAZORPAY_ACCOUNT_NUMBER not set — seller withdrawals are disabled "
                    + "until RazorpayX is configured. Rest of the app is unaffected.");
        }
    }

    public boolean isEnabled() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    public boolean payoutsEnabled() {
        return isEnabled() && accountNumber != null && !accountNumber.isBlank();
    }
}
