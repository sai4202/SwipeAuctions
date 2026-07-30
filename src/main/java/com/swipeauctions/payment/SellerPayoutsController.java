package com.swipeauctions.payment;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Seller/dealer Razorpay payout account — bank details submitted directly (no OAuth-redirect
 *  onboarding like Stripe Connect had); required before {@code /api/wallet/withdraw}. */
@RestController
@RequestMapping("/api/seller/payouts")
@RequiredArgsConstructor
public class SellerPayoutsController {

    private final RazorpayPaymentService razorpayPaymentService;
    private final LoggedInUserUtil loggedInUserUtil;

    @PostMapping("/account")
    public StatusResponse saveAccount(@Valid @RequestBody AccountRequest req) {
        User seller = loggedInUserUtil.getCurrentUser();
        User updated = razorpayPaymentService.ensurePayoutAccount(
                seller, req.accountHolderName(), req.accountNumber(), req.ifsc());
        return toStatus(updated);
    }

    @GetMapping("/status")
    public StatusResponse status() {
        User seller = loggedInUserUtil.getCurrentUser();
        return toStatus(seller);
    }

    private StatusResponse toStatus(User seller) {
        return new StatusResponse(seller.getRazorpayFundAccountId() != null,
                Boolean.TRUE.equals(seller.getRazorpayPayoutsEnabled()));
    }

    public record AccountRequest(
            @NotBlank String accountHolderName, @NotBlank String accountNumber, @NotBlank String ifsc) {}

    public record StatusResponse(boolean connected, boolean payoutsEnabled) {}
}
