package com.swipeauctions.wallet.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.bidding.repository.BidRepository;
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
 * topUp/withdraw are driven by RazorpayPaymentService once a real payment/payout is confirmed.
 * Balances (available/held) always reconcile against the summed transaction log.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txnRepository;
    private final AuctionRepository auctionRepository;
    private final BidEligibilityHoldRepository holdRepository;
    private final WalletWithdrawalRepository withdrawalRepository;
    private final SaleProceedsHoldRepository proceedsRepository;
    private final AuctionNotificationService notificationService;
    private final BidRepository bidRepository;

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

    /**
     * Sum of this bidder's own current (max) bid across every auction they're still actively
     * bidding on (status OPEN) — the amount presently "committed" out of their credit limit. An
     * auction that closes simply stops appearing in this sum on the next call, which is what frees
     * a losing bidder's exposure back up automatically (and, for a winner, the real charge is handled
     * separately by the settlement/captureRemainder flow against the wallet balance itself).
     */
    @Transactional(readOnly = true)
    public BigDecimal committedCredit(UUID bidderId) {
        return bidRepository.findMaxBidPerOpenAuctionForBidder(bidderId).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Credit limit minus whatever's already committed to the bidder's other open auctions — what
     *  they can actually still bid up to, right now, across everything. */
    @Transactional(readOnly = true)
    public BigDecimal availableCreditLimit(User user) {
        return getCreditLimit(user).subtract(committedCredit(user.getId()));
    }

    /**
     * Real-money deposit locked up in proportion to how much of the leveraged credit limit is
     * currently committed to open bids — same 5,000-per-2.5-crore ratio used to grant the credit
     * limit in the first place, just inverted. E.g. a ₹10,000 deposit grants ₹5 crore of credit; if
     * ₹2.5 crore of that is committed to open bids (half the limit), half the deposit — ₹5,000 — is
     * held back from withdrawal. Computed live from {@link #committedCredit}, so it shrinks the
     * moment a bid's auction closes without a bid loses or an admin refunds one — no separate
     * release step needed, unlike the real {@link BidEligibilityHold} EMD mechanism.
     */
    @Transactional(readOnly = true)
    public BigDecimal creditHeldAmount(User user) {
        BigDecimal committed = committedCredit(user.getId());
        if (committed.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal leverage = CREDIT_LIMIT_PER_UNIT.divide(CREDIT_LIMIT_DEPOSIT_UNIT);
        // Rounds up — never let the computed hold undershoot what's actually backing the bidder's
        // open exposure, which would let more than the safe amount be withdrawn.
        return committed.divide(leverage, 2, java.math.RoundingMode.CEILING);
    }

    /** What's actually free to withdraw right now: available balance minus whatever's held back by
     *  {@link #creditHeldAmount}. */
    @Transactional(readOnly = true)
    public BigDecimal withdrawableBalance(User user) {
        BigDecimal withdrawable = getWallet(user).getAvailableBalance().subtract(creditHeldAmount(user));
        return withdrawable.signum() < 0 ? BigDecimal.ZERO : withdrawable;
    }

    /**
     * Row-locks the bidder's wallet for the rest of the caller's transaction, serializing
     * concurrent bid placements by the same bidder even across different auctions. Without this,
     * two simultaneous bids on two different open auctions each read {@link #committedCredit}
     * before the other's bid becomes visible, so both can pass the credit-limit check independently
     * and together push the bidder's total exposure past their cap. Blocking the second caller here
     * until the first commits means its committedCredit() read afterward always sees the first bid.
     */
    @Transactional
    public void lockForBidding(User bidder) {
        getOrCreateWallet(bidder);
        lockWallet(bidder.getId());
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

    /** Credits the wallet — called by RazorpayPaymentService once a top-up payment is confirmed
     *  (or directly, by the dev-only free top-up endpoint). */
    @Transactional
    public Wallet topUp(User user, BigDecimal amount) {
        Wallet w = creditAvailable(user, amount, WalletTxnType.TOPUP, "RAZORPAY_ORDER", null);
        notificationService.walletTopUp(user.getEmail(), amount);
        return w;
    }

    /** Credits a referral bonus to the referrer's wallet — called by ReferralService once the
     *  referred user's top-up satisfies the admin-configured minimum deposit. Kept out of
     *  ReferralService's own transaction boundary knowledge: this is the only place that touches
     *  the wallet, same "wallet-first" separation as every other credit path in this class. */
    @Transactional
    public Wallet creditReferralBonus(User referrer, BigDecimal amount, String refType, String refId) {
        Wallet w = creditAvailable(referrer, amount, WalletTxnType.REFERRAL_BONUS, refType, refId);
        notificationService.referralBonusCredited(referrer.getEmail(), amount);
        return w;
    }

    /** Admin manual wallet correction (disputes, mistaken bids, or anything else that needs a
     *  one-off fix) — the human-readable reason isn't stored on {@link WalletTransaction} (no
     *  existing ledger row has one); it lives in the {@code AdminAuditLog} entry the controller
     *  writes right after, same as every other admin action's audit trail. */
    @Transactional
    public Wallet adminAdjust(User user, BigDecimal amount, boolean credit, String reason) {
        Wallet w;
        if (credit) {
            w = creditAvailable(user, amount, WalletTxnType.ADMIN_CREDIT, "ADMIN_ADJUSTMENT", null);
        } else {
            requirePositive(amount);
            getOrCreateWallet(user);
            w = lockWallet(user.getId());
            if (w.getAvailableBalance().compareTo(amount) < 0) {
                throw new BadRequestException("Insufficient available balance to debit " + amount);
            }
            w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
            walletRepository.save(w);
            record(w, WalletTxnType.ADMIN_DEBIT, amount, "ADMIN_ADJUSTMENT", null);
        }
        notificationService.walletAdjusted(user.getEmail(), amount, credit, reason);
        return w;
    }

    private Wallet creditAvailable(User user, BigDecimal amount, WalletTxnType type, String refType, String refId) {
        requirePositive(amount);
        getOrCreateWallet(user);
        Wallet w = lockWallet(user.getId());
        w.setAvailableBalance(w.getAvailableBalance().add(amount));
        walletRepository.save(w);
        record(w, type, amount, refType, refId);
        return w;
    }

    /** Register-to-bid: move the auction's base price from available → held (the bidding gate). */
    @Transactional
    public BidEligibilityHold placeHold(User bidder, Auction auction) {
        // Locks the auction row so this genuinely serializes against AuctionService.adminUpdate
        // (which locks the same row before changing basePrice) — without it, an admin's base-price
        // edit could commit in the window between this method reading auction.getBasePrice() and
        // saving the hold, sizing the hold to a price that's already stale. See Findings_pendings.md #4.
        Auction locked = auctionRepository.findByIdForUpdate(auction.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));
        holdRepository.findByAuction_IdAndBidder_Id(locked.getId(), bidder.getId())
                .ifPresent(h -> {
                    if (h.getStatus() == HoldStatus.ACTIVE) {
                        throw new BadRequestException("You are already registered to bid on this auction");
                    }
                });
        BigDecimal amount = locked.getBasePrice();
        getOrCreateWallet(bidder);
        Wallet w = lockWallet(bidder.getId());
        if (w.getAvailableBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient wallet balance. Top up at least " + amount + " to register to bid.");
        }
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        w.setHeldBalance(w.getHeldBalance().add(amount));
        walletRepository.save(w);
        record(w, WalletTxnType.HOLD, amount, "AUCTION", locked.getId().toString());

        BidEligibilityHold hold = holdRepository.findByAuction_IdAndBidder_Id(locked.getId(), bidder.getId())
                .orElseGet(() -> BidEligibilityHold.builder().auction(locked).bidder(bidder).build());
        hold.setAmount(amount);
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setResolvedAt(null);
        return holdRepository.save(hold);
    }

    /** Loser (or cancellation): return the held EMD to available balance. Returns the bidder's
     *  resulting credit limit so callers (auction close) can tell the bidder it just went back up. */
    @Transactional
    public BigDecimal releaseHold(Auction auction, User bidder) {
        BidEligibilityHold hold = activeHold(auction, bidder);
        Wallet w = lockWallet(bidder.getId());
        w.setHeldBalance(w.getHeldBalance().subtract(hold.getAmount()));
        w.setAvailableBalance(w.getAvailableBalance().add(hold.getAmount()));
        walletRepository.save(w);
        hold.setStatus(HoldStatus.RELEASED);
        hold.setResolvedAt(LocalDateTime.now());
        holdRepository.save(hold);
        record(w, WalletTxnType.RELEASE, hold.getAmount(), "AUCTION", auction.getId().toString());
        return creditLimitFor(w.getAvailableBalance());
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
     * allow, purely internally (no Razorpay call — same "wallet-first" rule as hold/release/capture).
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
     * withdrawal. {@link com.swipeauctions.payment.RazorpayPaymentService} fulfils it via a real
     * Razorpay Payout and marks the outcome — kept out of this class to avoid a circular
     * dependency (RazorpayPaymentService already depends on WalletService for top-up crediting).
     */
    @Transactional
    public WalletWithdrawal initiateWithdrawal(User user, BigDecimal amount) {
        requirePositive(amount);
        getOrCreateWallet(user);
        // Locks the wallet row before reading committedCredit (via creditHeldAmount) — the same row
        // BidService.placeBid locks via lockForBidding before its own committedCredit read, so a
        // concurrent bid and withdrawal serialize against each other instead of each reading a
        // pre-the-other's-change snapshot of what's committed.
        Wallet w = lockWallet(user.getId());
        BigDecimal withdrawable = w.getAvailableBalance().subtract(creditHeldAmount(user));
        if (withdrawable.signum() < 0) {
            withdrawable = BigDecimal.ZERO;
        }
        if (withdrawable.compareTo(amount) < 0) {
            throw new BadRequestException(
                    "Only " + withdrawable + " is available for withdrawal — the rest of your deposit is held "
                            + "against your currently committed bidding credit.");
        }
        w.setAvailableBalance(w.getAvailableBalance().subtract(amount));
        walletRepository.save(w);
        record(w, WalletTxnType.WITHDRAWAL, amount, "WALLET_WITHDRAWAL", null);
        return withdrawalRepository.save(WalletWithdrawal.builder()
                .wallet(w).amount(amount).status(WithdrawalStatus.PENDING).build());
    }

    @Transactional
    public void markWithdrawalSucceeded(WalletWithdrawal withdrawal, String razorpayPayoutId) {
        withdrawal.setStatus(WithdrawalStatus.SUCCEEDED);
        withdrawal.setRazorpayPayoutId(razorpayPayoutId);
        withdrawal.setCompletedAt(LocalDateTime.now());
        withdrawalRepository.save(withdrawal);
    }

    /** Razorpay Payout failed after the wallet was already debited — give the funds back. */
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
        SaleProceedsHold locked = lockProceedsHold(hold.getId());
        requireActive(locked);
        Wallet w = lockWallet(locked.getSeller().getId());
        w.setHeldBalance(w.getHeldBalance().subtract(locked.getAmount()));
        w.setAvailableBalance(w.getAvailableBalance().add(locked.getAmount()));
        walletRepository.save(w);
        locked.setStatus(ProceedsStatus.RELEASED);
        locked.setResolvedAt(LocalDateTime.now());
        proceedsRepository.save(locked);
        record(w, WalletTxnType.RELEASE, locked.getAmount(), "AUCTION", locked.getAuction().getId().toString());
    }

    /**
     * Admin upheld a dispute: reverses the still-escrowed proceeds back to the buyer instead of
     * the seller. Only possible while the hold is ACTIVE — if it already auto-released (seller may
     * have withdrawn since), this throws rather than risk taking a seller's wallet negative.
     */
    @Transactional
    public void refundSaleProceeds(SaleProceedsHold hold, User buyer) {
        SaleProceedsHold locked = lockProceedsHold(hold.getId());
        requireActive(locked);
        Wallet sellerWallet = lockWallet(locked.getSeller().getId());
        sellerWallet.setHeldBalance(sellerWallet.getHeldBalance().subtract(locked.getAmount()));
        walletRepository.save(sellerWallet);
        record(sellerWallet, WalletTxnType.DEBIT, locked.getAmount(), "DISPUTE_REFUND", locked.getAuction().getId().toString());

        getOrCreateWallet(buyer);
        Wallet buyerWallet = lockWallet(buyer.getId());
        buyerWallet.setAvailableBalance(buyerWallet.getAvailableBalance().add(locked.getAmount()));
        walletRepository.save(buyerWallet);
        record(buyerWallet, WalletTxnType.REFUND, locked.getAmount(), "DISPUTE_REFUND", locked.getAuction().getId().toString());

        locked.setStatus(ProceedsStatus.REFUNDED);
        locked.setResolvedAt(LocalDateTime.now());
        proceedsRepository.save(locked);
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

    /** Locks the hold row before checking ACTIVE status — see Findings_pendings.md #3: a
     *  non-locking read-then-check here let two concurrent callers (admin double-click, or the
     *  auto-release scheduler racing a manual release) both pass and both apply their credit. */
    private BidEligibilityHold activeHold(Auction auction, User bidder) {
        BidEligibilityHold hold = holdRepository.findByAuction_IdAndBidder_IdForUpdate(auction.getId(), bidder.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No EMD hold found for this bidder/auction"));
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new BadRequestException("EMD hold is not active");
        }
        return hold;
    }

    private SaleProceedsHold lockProceedsHold(UUID id) {
        return proceedsRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale proceeds hold not found"));
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
