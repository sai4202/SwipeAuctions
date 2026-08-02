package com.swipeauctions.wallet.controller;

import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.payment.RazorpayPaymentService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.entity.WalletTransaction;
import com.swipeauctions.wallet.entity.WalletWithdrawal;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import com.swipeauctions.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** View wallet balance, and — once Razorpay is configured — real top-up/withdraw. */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final RazorpayPaymentService razorpayPaymentService;
    private final WalletTransactionRepository txnRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping
    public WalletResponse balance() {
        User user = loggedInUserUtil.getCurrentUser();
        Wallet w = walletService.getWallet(user);
        return toResponse(w, user);
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

    private WalletResponse toResponse(Wallet w, User user) {
        BigDecimal creditLimit = walletService.creditLimitFor(w.getAvailableBalance());
        BigDecimal available = creditLimit.subtract(walletService.committedCredit(user.getId()));
        BigDecimal creditHeld = walletService.creditHeldAmount(user);
        BigDecimal withdrawable = w.getAvailableBalance().subtract(creditHeld);
        if (withdrawable.signum() < 0) {
            withdrawable = BigDecimal.ZERO;
        }
        return new WalletResponse(w.getAvailableBalance(), w.getHeldBalance(), creditLimit, available,
                creditHeld, withdrawable);
    }

    private WalletTransactionResponse toResponse(WalletTransaction t) {
        return new WalletTransactionResponse(t.getId(), t.getType().name(), t.getAmount(),
                t.getReferenceType(), t.getReferenceId(), t.getCreatedAt());
    }

    /** Real top-up: creates a Razorpay Order for the frontend to open Checkout against. */
    @PostMapping("/topup/order")
    public RazorpayPaymentService.OrderIntent createTopUpOrder(@Valid @RequestBody TopUpRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        return razorpayPaymentService.createTopUpOrder(user, req.amount());
    }

    /** Called right after Razorpay Checkout succeeds — verifies the signature and credits the
     *  wallet (the webhook also confirms independently; whichever arrives first wins). */
    @PostMapping("/topup/verify")
    public WalletResponse verifyTopUp(@Valid @RequestBody VerifyPaymentRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        razorpayPaymentService.verifyAndSettle(user, req.orderId(), req.paymentId(), req.signature());
        return toResponse(walletService.getWallet(user), user);
    }

    /** Real withdraw: debits the wallet and pays out via a Razorpay Payout to the seller's bank account. */
    @PostMapping("/withdraw")
    public WithdrawResponse withdraw(@Valid @RequestBody TopUpRequest req) {
        User user = loggedInUserUtil.getCurrentUser();
        WalletWithdrawal withdrawal = razorpayPaymentService.withdraw(user, req.amount());
        Wallet w = walletService.getWallet(user);
        return new WithdrawResponse(withdrawal.getStatus().name(), w.getAvailableBalance());
    }

    public record TopUpRequest(@NotNull BigDecimal amount) {}

    public record VerifyPaymentRequest(
            @NotBlank String orderId, @NotBlank String paymentId, @NotBlank String signature) {}

    public record WalletResponse(BigDecimal availableBalance, BigDecimal heldBalance, BigDecimal creditLimit,
                                  BigDecimal availableCreditLimit, BigDecimal creditHeldAmount,
                                  BigDecimal withdrawableBalance) {}

    public record WithdrawResponse(String status, BigDecimal availableBalance) {}

    public record WalletTransactionResponse(
            UUID id, String type, BigDecimal amount, String referenceType, String referenceId, LocalDateTime createdAt) {}
}
