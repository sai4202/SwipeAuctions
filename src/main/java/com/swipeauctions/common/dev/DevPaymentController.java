package com.swipeauctions.common.dev;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.controller.WalletController;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Dev-only instant wallet credit — bypasses Razorpay entirely. Only exists under the "dev" profile
 * ({@code --spring.profiles.active=dev}), unlike the endpoint this replaces, which had no profile
 * gate and was reachable in every environment (see Findings_pendings.md #1) — local/demo testing
 * convenience without needing to complete a real Razorpay Checkout every time.
 */
@RestController
@RequestMapping("/api/wallet")
@Profile("dev")
@RequiredArgsConstructor
public class DevPaymentController {

    private final WalletService walletService;
    private final LoggedInUserUtil loggedInUserUtil;

    @PostMapping("/topup")
    public WalletController.WalletResponse topUp(@Valid @RequestBody TopUpRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        Wallet w = walletService.topUp(user, req.amount());
        BigDecimal creditLimit = walletService.creditLimitFor(w.getAvailableBalance());
        BigDecimal available = creditLimit.subtract(walletService.committedCredit(user.getId()));
        BigDecimal creditHeld = walletService.creditHeldAmount(user);
        BigDecimal withdrawable = w.getAvailableBalance().subtract(creditHeld);
        if (withdrawable.signum() < 0) {
            withdrawable = BigDecimal.ZERO;
        }
        return new WalletController.WalletResponse(w.getAvailableBalance(), w.getHeldBalance(), creditLimit,
                available, creditHeld, withdrawable);
    }

    public record TopUpRequest(@NotNull BigDecimal amount) {}
}
