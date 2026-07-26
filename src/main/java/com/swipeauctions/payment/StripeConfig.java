package com.swipeauctions.payment;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sets the Stripe SDK's global API key at startup. {@code secretKey} is blank by default (no env
 * var set) — Stripe-backed endpoints then reject with a clear "not configured" error instead of
 * the app failing to boot, so the rest of the platform works without Stripe keys present.
 */
@Component
@Slf4j
public class StripeConfig {

    @Getter
    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Getter
    @Value("${stripe.publishable-key:}")
    private String publishableKey;

    @Getter
    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Getter
    @Value("${stripe.connect.return-url}")
    private String connectReturnUrl;

    @Getter
    @Value("${stripe.connect.refresh-url}")
    private String connectRefreshUrl;

    @PostConstruct
    void init() {
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("[stripe] STRIPE_SECRET_KEY not set — wallet top-up/withdraw and seller Connect "
                    + "onboarding are disabled until it's configured. Rest of the app is unaffected.");
            return;
        }
        Stripe.apiKey = secretKey;
        log.info("[stripe] configured ({} mode)", secretKey.startsWith("sk_live_") ? "LIVE" : "test");
    }

    public boolean isEnabled() {
        return secretKey != null && !secretKey.isBlank();
    }
}
