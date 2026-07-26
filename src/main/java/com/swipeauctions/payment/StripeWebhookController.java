package com.swipeauctions.payment;

import com.swipeauctions.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Public endpoint Stripe calls directly (no JWT) — the payload's Stripe-Signature is the auth. */
@RestController
@RequestMapping("/api/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripePaymentService stripePaymentService;

    @PostMapping
    public ApiResponse<String> handle(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature
    ) {
        stripePaymentService.handleWebhook(payload, signature);
        return ApiResponse.success("received", null);
    }
}
