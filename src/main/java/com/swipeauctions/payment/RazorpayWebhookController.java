package com.swipeauctions.payment;

import com.swipeauctions.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Public endpoint Razorpay calls directly (no JWT) — the payload's X-Razorpay-Signature is the auth. */
@RestController
@RequestMapping("/api/webhooks/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayPaymentService razorpayPaymentService;

    @PostMapping
    public ApiResponse<String> handle(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        razorpayPaymentService.handleWebhook(payload, signature);
        return ApiResponse.success("received", null);
    }
}
