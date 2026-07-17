package com.swipeauctions.wallet.controller;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** View wallet balance and (dev) top up. Real Stripe funding lands in Phase 2. */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping
    public WalletResponse balance() {
        User user = loggedInUserUtil.getCurrentUser();
        Wallet w = walletService.getWallet(user);
        return new WalletResponse(w.getAvailableBalance(), w.getHeldBalance());
    }

    @PostMapping("/topup")
    public WalletResponse topUp(@Valid @RequestBody TopUpRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        Wallet w = walletService.topUp(user, req.amount());
        return new WalletResponse(w.getAvailableBalance(), w.getHeldBalance());
    }

    public record TopUpRequest(@NotNull BigDecimal amount) {}

    public record WalletResponse(BigDecimal availableBalance, BigDecimal heldBalance) {}
}
