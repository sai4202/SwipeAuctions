package com.swipeauctions.wallet.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.entity.SaleProceedsHold;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.entity.WalletTransaction;
import com.swipeauctions.wallet.entity.WalletWithdrawal;
import com.swipeauctions.wallet.enums.HoldStatus;
import com.swipeauctions.wallet.enums.ProceedsStatus;
import com.swipeauctions.wallet.enums.WalletTxnType;
import com.swipeauctions.wallet.enums.WithdrawalStatus;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.repository.SaleProceedsHoldRepository;
import com.swipeauctions.wallet.repository.WalletRepository;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import com.swipeauctions.wallet.repository.WalletWithdrawalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Internal wallet ledger. hold/release/capture are pure in-house balance moves (no external calls);
 * only topUp/withdraw touch real money (Stripe) — stubbed as internal credit until Phase 2.
 * Balances (available/held) always reconcile against the summed transaction log.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txnRepository;
    private final BidEligibilityHoldRepository holdRepository;
    private final WalletWithdrawalRepository withdrawalRepository;
    private final SaleProceedsHoldRepository proceedsRepository;
    private final AuctionNotificationService notificationService;

    private static final BigDecimal CREDIT_LIMIT_DEPOSIT_UNIT = new BigDecimal("5000");
    private static final BigDecimal CREDIT_LIMIT_PER_UNIT = new BigDecimal("25000000"); // ₹2.5 crore

    /** Leverage-style bidding cap: every complete ₹5,000 of available balance grants ₹2.5 crore of credit. */
    public BigDecimal creditLimitFor(BigDecimal availableBalance) {
        if (availableBalance == null || availableBalance.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return availableBalance.divideToIntegralValue(CREDIT_LIMIT_DEPOSIT_UNIT).multiply(CREDIT_LIMIT_PER_UNIT);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCreditLimit(User user) {
        return creditLimitFor(getWallet(user).getAvailableBalance());
    }

    @Transactional
    public Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUser_Id(user.getId())
                .orElseGet(() -> walletRepository.save(Wallet.builder().user(user).build()));
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(User user) {
        return walletRepository.findByUser_Id(user.getId())
                .orElseGet(() -> Wallet.builder().user(user).build());
    }

    /** Dev funding — internal credit. Phase 2 replaces this with a real Stripe PaymentIntent. */
    @Transactional
    public Wallet topUp(User user, BigDecimal amount) {
        requirePositive(amount);
        getOrCreateWallet(user);
        Wallet w = lockWallet(user.getId());
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        walletRepository.save(w);
        record(w, WalletTxnType.TOPUP, amount, "STRIPE_PAYMENT_INTENT", null);
        notificationService.walletTopUp(user.getEmail(), amount);
        return w;
    }

    /** Register-to-bid: move the auction's base price from available → held (the bidding gate). */
    @Transactional
    public BidEligibilityHold placeHold(User bidder, Auction auction) {
        holdRepository.findByAuction_IdAndBidder_Id(auction.getId(), bidder.getId())
                .ifPresent(h -> {
                    if (h.getStatus() == HoldStatus.ACTIVE) {
                        throw new BadRequestException("You are already registered to bid on this auction");
                    }
                });
        BigDecimal amount = auction.getBasePrice();
        getOrCreateWallet(bidder);
        Wallet w = lockWallet(bidder.getId());
        if (w.getAvailableBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient wallet balance. Top up at least " + amount + " to register to bid.");
        }
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        w.setHeldBalance(w.getHeldBalance().add(amount));
        walletRepository.save(w);
        record(w, WalletTxnType.HOLD, amount, "AUCTION", auction.getId().toString());

        BidEligibilityHold hold = holdRepository.findByAuction_IdAndBidder_Id(auction.getId(), bidder.getId())
                .orElseGet(() -> BidEligibilityHold.builder().auction(auction).bidder(bidder).build());
        hold.setAmount(amount);
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setResolvedAt(null);
        return holdRepository.save(hold);
    }

    /** Loser (or cancellation): return the held EMD to available balance. */
    @Transactional
    public void releaseHold(Auction auction, User bidder) {
        BidEligibilityHold hold = activeHold(auction, bidder);
        Wallet w = lockWallet(bidder.getId());
        w.setHeldBalance(w.getHeldBalance().subtract(hold.getAmount()));
        w.setAvailableBalance(w.getAvailableBalance().add(hold.getAmount()));
        walletRepository.save(w);
        hold.setStatus(HoldStatus.RELEASED);
        hold.setResolvedAt(LocalDateTime.now());
        holdRepository.save(hold);
        record(w, WalletTxnType.RELEASE, hold.getAmount(), "AUCTION", auction.getId().toString());
    }

    /** Winner: capture the held EMD (funds leave the wallet toward settlement). */
    @Transactional
    public void captureHold(Auction auction, User bidder) {
        BidEligibilityHold hold = activeHold(auction, bidder);
        Wallet w = lockWallet(bidder.getId());
        w.setHeldBalance(w.getHeldBalance().subtract(hold.getAmount()));
        walletRepository.save(w);
        hold.setStatus(HoldStatus.CAPTURED);
        hold.setResolvedAt(LocalDateTime.now());
        holdRepository.save(hold);
        record(w, WalletTxnType.CAPTURE, hold.getAmount(), "AUCTION", auction.getId().toString());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveHold(UUID auctionId, UUID bidderId) {
        return holdRepository.existsByAuction_IdAndBidder_IdAndStatus(auctionId, bidderId, HoldStatus.ACTIVE);
    }

    /** Batch variant of {@link #hasActiveHold} for the auction browse list — one round trip total. */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> auctionIdsWithActiveHold(List<UUID> auctionIds, UUID bidderId) {
        return new java.util.HashSet<>(holdRepository.findAuctionIdsWithActiveHold(bidderId, auctionIds, HoldStatus.ACTIVE));
    }

    /**
     * Winner settlement remainder: the final winning bid may exceed the EMD (base price) already
     * captured by {@link #captureHold}. Debits the difference from available balance if funds
     * allow, purely internally (no Stripe call — same "wallet-first" rule as hold/release/capture).
     * Returns false (no state changed) if the winner doesn't have enough available balance yet;
     * the caller can retry later once they've topped up.
     */
    @Transactional
    public boolean captureRemainder(Auction auction, User winner, BigDecimal remainder) {
        if (remainder.signum() <= 0) {
            return true;
        }
        Wallet w = lockWallet(winner.getId());
        if (w.getAvailableBalance().compareTo(remainder) < 0) {
            return false;
        }
        w.setAvailableBalance(w.getAvailableBalance().subtract(remainder));
        walletRepository.save(w);
        record(w, WalletTxnType.DEBIT, remainder, "AUCTION_SETTLEMENT", auction.getId().toString());
        return true;
    }

    /**
     * Seller/dealer payout request: debits available balance immediately and records a PENDING
     * withdrawal. {@link com.swipeauctions.payment.StripePaymentService} fulfils it via a real
     * Stripe Transfer and marks the outcome — kept out of this class to avoid a circular
     * dependency (StripePaymentService already depends on WalletService for top-up crediting).
     */
    @Transactional
    public WalletWithdrawal initiateWithdrawal(User user, BigDecimal amount) {
        requirePositive(amount);
        getOrCreateWallet(user);
        Wallet w = lockWallet(user.getId());
        if (w.getAvailableBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient available balance to withdraw " + amount);
        }
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        walletRepository.save(w);
        record(w, WalletTxnType.WITHDRAWAL, amount, "WALLET_WITHDRAWAL", null);
        return withdrawalRepository.save(WalletWithdrawal.builder()
                .wallet(w).amount(amount).status(WithdrawalStatus.PENDING).build());
    }

    @Transactional
    public void markWithdrawalSucceeded(WalletWithdrawal withdrawal, String stripeTransferId) {
        withdrawal.setStatus(WithdrawalStatus.SUCCEEDED);
        withdrawal.setStripeTransferId(stripeTransferId);
        withdrawal.setCompletedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);
    }

    /** Stripe Transfer failed after the wallet was already debited — give the funds back. */
    @Transactional
    public void markWithdrawalFailed(WalletWithdrawal withdrawal) {
        Wallet w = lockWallet(withdrawal.getWallet().getUser().getId());
        w.setAvailableBalance(w.getAvailableBalance().add(withdrawal.getAmount()));
        walletRepository.save(w);
        record(w, WalletTxnType.REFUND, withdrawal.getAmount(), "WALLET_WITHDRAWAL_FAILED", withdrawal.getId().toString());
        withdrawal.setStatus(WithdrawalStatus.FAILED);
        withdrawal.setCompletedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);
    }

    /**
     * Credits the seller's escrowed sale proceeds for one auction, into held balance (not
     * available) — the escrow is what makes {@link #releaseSaleProceeds} / {@link
     * #refundSaleProceeds} meaningful. One per auction (a listing can only be auctioned once).
     */
    @Transactional
    public SaleProceedsHold creditSaleProceeds(Auction auction, User seller, BigDecimal amount) {
        if (proceedsRepository.findByAuction_Id(auction.getId()).isPresent()) {
            throw new BadRequestException("Sale proceeds already credited for this auction");
        }
        getOrCreateWallet(seller);
        Wallet w = lockWallet(seller.getId());
        w.setHeldBalance(w.getHeldBalance().add(amount));
        walletRepository.save(w);
        record(w, WalletTxnType.SALE_PROCEEDS, amount, "AUCTION", auction.getId().toString());
        return proceedsRepository.save(SaleProceedsHold.builder()
                .auction(auction).seller(seller).amount(amount).status(ProceedsStatus.ACTIVE).build());
    }

    /** Escrow window elapsed with no open dispute, or an admin resolved the dispute in the seller's favor. */
    @Transactional
    public void releaseSaleProceeds(SaleProceedsHold hold) {
        requireActive(hold);
        Wallet w = lockWallet(hold.getSeller().getId());
        w.setHeldBalance(w.getHeldBalance().subtract(hold.getAmount()));
        w.setAvailableBalance(w.getAvailableBalance().add(hold.getAmount()));
        walletRepository.save(w);
        hold.setStatus(ProceedsStatus.RELEASED);
        hold.setResolvedAt(LocalDateTime.now());
        proceedsRepository.save(hold);
        record(w, WalletTxnType.RELEASE, hold.getAmount(), "AUCTION", hold.getAuction().getId().toString());
    }

    /**
     * Admin upheld a dispute: reverses the still-escrowed proceeds back to the buyer instead of
     * the seller. Only possible while the hold is ACTIVE — if it already auto-released (seller may
     * have withdrawn since), this throws rather than risk taking a seller's wallet negative.
     */
    @Transactional
    public void refundSaleProceeds(SaleProceedsHold hold, User buyer) {
        requireActive(hold);
        Wallet sellerWallet = lockWallet(hold.getSeller().getId());
        sellerWallet.setHeldBalance(sellerWallet.getHeldBalance().subtract(hold.getAmount()));
        walletRepository.save(sellerWallet);
        record(sellerWallet, WalletTxnType.DEBIT, hold.getAmount(), "DISPUTE_REFUND", hold.getAuction().getId().toString());

        getOrCreateWallet(buyer);
        Wallet buyerWallet = lockWallet(buyer.getId());
        buyerWallet.setAvailableBalance(buyerWallet.getAvailableBalance().add(hold.getAmount()));
        walletRepository.save(buyerWallet);
        record(buyerWallet, WalletTxnType.REFUND, hold.getAmount(), "DISPUTE_REFUND", hold.getAuction().getId().toString());

        hold.setStatus(ProceedsStatus.REFUNDED);
        hold.setResolvedAt(LocalDateTime.now());
        proceedsRepository.save(hold);
    }

    /** Escrow holds past the release window — the caller filters out ones with an open dispute. */
    @Transactional(readOnly = true)
    public List<SaleProceedsHold> findProceedsDueForRelease(LocalDateTime createdBefore) {
        return proceedsRepository.findByStatusAndCreatedAtBefore(ProceedsStatus.ACTIVE, createdBefore);
    }

    private void requireActive(SaleProceedsHold hold) {
        if (hold.getStatus() != ProceedsStatus.ACTIVE) {
            throw new BadRequestException("Sale proceeds are not in escrow anymore (already " + hold.getStatus() + ")");
        }
    }

    // ---- helpers ----

    private Wallet lockWallet(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }

    private BidEligibilityHold activeHold(Auction auction, User bidder) {
        BidEligibilityHold hold = holdRepository.findByAuction_IdAndBidder_Id(auction.getId(), bidder.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No EMD hold found for this bidder/auction"));
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new BadRequestException("EMD hold is not active");
        }
        return hold;
    }

    private void record(Wallet w, WalletTxnType type, BigDecimal amount, String refType, String refId) {
        txnRepository.save(WalletTransaction.builder()
                .wallet(w).type(type).amount(amount).referenceType(refType).referenceId(refId).build());
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
    }
}
