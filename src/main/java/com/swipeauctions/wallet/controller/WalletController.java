package com.swipeauctions.wallet.controller;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.payment.StripePaymentService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.entity.WalletTransaction;
import com.swipeauctions.wallet.entity.WalletWithdrawal;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import com.swipeauctions.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** View wallet balance, (dev) top up, and — once Stripe is configured — real top-up/withdraw. */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final StripePaymentService stripePaymentService;
    private final WalletTransactionRepository txnRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping
    public WalletResponse balance() {
        User user = loggedInUserUtil.getCurrentUser();
        Wallet w = walletService.getWallet(user);
        return new WalletResponse(w.getAvailableBalance(), w.getHeldBalance());
    }

    /** This user's wallet ledger, most recent first. */
    @GetMapping("/transactions")
    public List<WalletTransactionResponse> transactions() {
        User user = loggedInUserUtil.getCurrentUser();
        Wallet w = walletService.getWallet(user);
        if (w.getId() == null) return List.of();
        return txnRepository.findByWallet_IdOrderByCreatedAtDesc(w.getId()).stream()
                .map(this::toResponse).toList();
    }

    private WalletTransactionResponse toResponse(WalletTransaction t) {
        return new WalletTransactionResponse(t.getId(), t.getType().name(), t.getAmount(),
                t.getReferenceType(), t.getReferenceId(), t.getCreatedAt());
    }

    /** Dev-only instant credit — bypasses Stripe entirely. Left in place for local/demo testing. */
    @PostMapping("/topup")
    public WalletResponse topUp(@Valid @RequestBody TopUpRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        Wallet w = walletService.topUp(user, req.amount());
        return new WalletResponse(w.getAvailableBalance(), w.getHeldBalance());
    }

    /** Real top-up: creates a Stripe PaymentIntent; the wallet is credited by the success webhook. */
    @PostMapping("/topup/intent")
    public StripePaymentService.TopUpIntent createTopUpIntent(@Valid @RequestBody TopUpRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        return stripePaymentService.createTopUpIntent(user, req.amount());
    }

    /** Real withdraw: debits the wallet and pays out via Stripe Transfer to the seller's Connect account. */
    @PostMapping("/withdraw")
    public WithdrawResponse withdraw(@Valid @RequestBody TopUpRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        WalletWithdrawal withdrawal = stripePaymentService.withdraw(user, req.amount());
        Wallet w = walletService.getWallet(user);
        return new WithdrawResponse(withdrawal.getStatus().name(), w.getAvailableBalance());
    }

    public record TopUpRequest(@NotNull BigDecimal amount) {}

    public record WalletResponse(BigDecimal availableBalance, BigDecimal heldBalance) {}

    public record WithdrawResponse(String status, BigDecimal availableBalance) {}

    public record WalletTransactionResponse(
            UUID id, String type, BigDecimal amount, String referenceType, String referenceId, LocalDateTime createdAt) {}
}
