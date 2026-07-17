package com.swipeauctions.wallet.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.entity.WalletTransaction;
import com.swipeauctions.wallet.enums.HoldStatus;
import com.swipeauctions.wallet.enums.WalletTxnType;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.repository.WalletRepository;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /** Loser (or cancellation): return the held deposit to available balance. */
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

    /** Winner: capture the held deposit (funds leave the wallet toward settlement). */
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

    // ---- helpers ----

    private Wallet lockWallet(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }

    private BidEligibilityHold activeHold(Auction auction, User bidder) {
        BidEligibilityHold hold = holdRepository.findByAuction_IdAndBidder_Id(auction.getId(), bidder.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No deposit hold found for this bidder/auction"));
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new BadRequestException("Deposit hold is not active");
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
