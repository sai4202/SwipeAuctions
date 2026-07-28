package com.swipeauctions.bidding;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.bidding.entity.Bid;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.bidding.service.BidService;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.repository.CategoryRepository;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.catalog.service.CatalogService;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.enums.Role;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.repository.WalletRepository;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import com.swipeauctions.wallet.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one test that needs real Postgres row-locking semantics (pessimistic lock on the auction
 * row, wallet row). Runs against the real dev Supabase DB — same connection every manual/curl
 * verification in this project already uses — per the "no Docker on this machine" decision (see
 * TASKS.md / structured-riding-trinket.md). Creates and tears down its own throwaway fixtures via
 * the same services DevDataSeeder uses, not raw SQL.
 */
@SpringBootTest
class BidServiceConcurrencyTest {

    @Autowired private BidService bidService;
    @Autowired private AuctionService auctionService;
    @Autowired private CatalogService catalogService;
    @Autowired private WalletService walletService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private BidRepository bidRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String suffix;
    private User seller;
    private User bidder1;
    private User bidder2;
    private User bidderNoCredit;
    private User bidderTight;
    private Listing listing;
    private Listing listing2;
    private Auction auction;
    private Auction auction2;
    private final List<Wallet> wallets = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Fresh per test method (not shared across the class) so a partially-failed teardown in one
        // test can never collide with the next test's unique email/mobile/reference-number columns.
        suffix = UUID.randomUUID().toString().substring(0, 8);
        String mobileBase = String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000);

        Category category = categoryRepository.findBySlug("concurrency-test")
                .orElseGet(() -> catalogService.createCategory("Concurrency Test", "concurrency-test", null));

        seller = createUser("concurrency-seller-" + suffix + "@swipeauctions.test", "97" + mobileBase + "01", Role.USER);
        bidder1 = createUser("concurrency-bidder1-" + suffix + "@swipeauctions.test", "97" + mobileBase + "02", Role.USER);
        bidder2 = createUser("concurrency-bidder2-" + suffix + "@swipeauctions.test", "97" + mobileBase + "03", Role.USER);
        bidderNoCredit = createUser("concurrency-nohold-" + suffix + "@swipeauctions.test", "97" + mobileBase + "04", Role.USER);
        bidderTight = createUser("concurrency-tight-" + suffix + "@swipeauctions.test", "97" + mobileBase + "05", Role.USER);

        wallets.add(walletService.topUp(bidder1, new BigDecimal("100000.00")));
        wallets.add(walletService.topUp(bidder2, new BigDecimal("100000.00")));
        // Deliberately never topped up — zero credit limit is what the gating test below relies on.
        // Exactly one credit-limit unit (₹5,000 -> ₹2.5 crore) — precise enough to test the shared
        // cross-auction cap without astronomically large numbers.
        wallets.add(walletService.topUp(bidderTight, new BigDecimal("5000.00")));

        listing = catalogService.createListing(seller, category.getId(), "Concurrency test item " + suffix,
                "Created by BidServiceConcurrencyTest", null, ItemCondition.USED, "Test City", "TS", null,
                new BigDecimal("1000.00"), null);
        listing2 = catalogService.createListing(seller, category.getId(), "Concurrency test item 2 " + suffix,
                "Second item — used only by the cross-auction credit-limit test.", null, ItemCondition.USED,
                "Test City", "TS", null, new BigDecimal("1000.00"), null);

        LocalDateTime now = LocalDateTime.now();
        auction = auctionService.createAuction(seller, listing.getId(), new BigDecimal("1000.00"),
                now.minusMinutes(1), now.plusMinutes(30), null);
        auction2 = auctionService.createAuction(seller, listing2.getId(), new BigDecimal("1000.00"),
                now.minusMinutes(1), now.plusMinutes(30), null);
    }

    @AfterEach
    void tearDown() {
        bidRepository.deleteAll(bidRepository.findByAuction_IdOrderByAmountDesc(auction.getId()));
        bidRepository.deleteAll(bidRepository.findByAuction_IdOrderByAmountDesc(auction2.getId()));
        for (Wallet w : wallets) {
            walletTransactionRepository.deleteAll(walletTransactionRepository.findByWallet_IdOrderByCreatedAtDesc(w.getId()));
        }
        walletRepository.deleteAll(wallets);
        // Refetch: the concurrent bidding test bumps the auction's @Version many times, so the
        // in-memory reference captured at setUp is stale and would fail an optimistic-lock check on delete.
        auctionRepository.findById(auction.getId()).ifPresent(auctionRepository::delete);
        auctionRepository.findById(auction2.getId()).ifPresent(auctionRepository::delete);
        listingRepository.delete(listing);
        listingRepository.delete(listing2);
        userRepository.delete(bidderNoCredit);
        userRepository.delete(bidderTight);
        userRepository.delete(bidder2);
        userRepository.delete(bidder1);
        userRepository.delete(seller);
        // The "Concurrency Test" category (created in setUp via findBySlug().orElseGet()) was the one
        // thing this test left behind permanently — it doesn't belong to any real listing (ours is
        // already deleted above), but it's a real row that showed up as a genuine-looking option in
        // the live category filter. setUp() recreates it on demand next run, so it's truly throwaway.
        categoryRepository.findBySlug("concurrency-test").ifPresent(categoryRepository::delete);
    }

    private User createUser(String email, String mobile, Role role) {
        return userRepository.save(User.builder()
                .role(role).email(email).mobileNumber(mobile)
                .password(passwordEncoder.encode("Test@1234"))
                .active(true).emailVerified(true).mobileVerified(true)
                .kycCompleted(true).kycStatus(KycStatus.APPROVED)
                .registrationFeePaid(true)
                .userRefNumber("CONC-" + mobile).build());
    }

    /**
     * Fires many concurrent bids (both registered bidders, strictly increasing amounts) at the same
     * auction row. The pessimistic lock in {@code AuctionRepository.findByIdForUpdate} must fully
     * serialize these — no lost update — regardless of how many individually-valid bids actually win
     * the race. We don't predict which bids win; we assert the invariant a broken lock would break:
     * the accepted bids form a strictly increasing ladder, and the auction's cached
     * currentHighestBid/bidCount exactly match that ladder's tail/length.
     */
    @Test
    void concurrentBids_serializeWithNoLostUpdate() throws InterruptedException {
        int threadsPerBidder = 8;
        int totalThreads = threadsPerBidder * 2;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 1; i <= threadsPerBidder; i++) {
            BigDecimal amountA = new BigDecimal("1000.00").add(BigDecimal.valueOf(i * 20L));
            BigDecimal amountB = new BigDecimal("1000.00").add(BigDecimal.valueOf(i * 20L + 10L));
            futures.add(submitBid(pool, ready, go, bidder1, amountA));
            futures.add(submitBid(pool, ready, go, bidder2, amountB));
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        int accepted = 0;
        for (Future<Boolean> f : futures) {
            try {
                if (Boolean.TRUE.equals(f.get(30, TimeUnit.SECONDS))) accepted++;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        List<Bid> acceptedBids = bidRepository.findByAuction_IdOrderByAmountDesc(auction.getId());
        assertThat(acceptedBids).hasSize(accepted);
        assertThat(accepted).isGreaterThan(0);

        // Ascending order (repository returns amount-desc) must be strictly increasing — no two
        // accepted bids at the same or lower amount than an earlier one.
        List<BigDecimal> ascending = acceptedBids.reversed().stream().map(Bid::getAmount).toList();
        for (int i = 1; i < ascending.size(); i++) {
            assertThat(ascending.get(i)).isGreaterThan(ascending.get(i - 1));
        }

        Auction refreshed = auctionRepository.findById(auction.getId()).orElseThrow();
        assertThat(refreshed.getCurrentHighestBid()).isEqualByComparingTo(ascending.get(ascending.size() - 1));
        assertThat(bidRepository.countByAuction_Id(auction.getId())).isEqualTo(accepted);
    }

    /** A bidder with zero credit limit (never deposited) must never win a bid, even racing against valid bidders. */
    @Test
    void bidderWithoutCreditLimit_isRejectedEvenUnderConcurrentLoad() throws InterruptedException {
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 1; i <= attempts; i++) {
            BigDecimal amount = new BigDecimal("1000.00").add(BigDecimal.valueOf(i * 50L));
            futures.add(submitBid(pool, ready, go, bidderNoCredit, amount));
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        for (Future<Boolean> f : futures) {
            try {
                assertThat(f.get(30, TimeUnit.SECONDS)).isFalse();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(bidRepository.countByAuction_Id(auction.getId())).isZero();
    }

    /**
     * The credit limit is a cap on TOTAL exposure across every auction a bidder is active on, not a
     * per-bid check taken in isolation. bidderTight has exactly ₹2.5 crore of credit limit (from a
     * ₹5,000 deposit). Bidding ₹2 crore on one auction leaves only ₹0.5 crore free — a second,
     * unrelated auction can't independently re-claim the full ₹2.5 crore. Closing the first auction
     * frees its share back up automatically (no separate "release" call needed), matching the
     * fact that a real settlement charge, when it happens, is a wallet debit handled elsewhere
     * (captureRemainder), not a mutation of this shared bidding-exposure check.
     */
    @Test
    void creditLimitIsASharedCapAcrossAllOfABiddersOpenAuctions() {
        BigDecimal twoCrore = new BigDecimal("20000000.00");
        BigDecimal oneCrore = new BigDecimal("10000000.00");
        BigDecimal halfCrore = new BigDecimal("5000000.00");

        // Full 2.5cr limit; committing 2cr to auction 1 leaves only 0.5cr free overall.
        bidService.placeBid(auction.getId(), bidderTight, twoCrore);

        // 1cr more on a DIFFERENT auction would push total exposure to 3cr, past the 2.5cr limit.
        assertThatThrownBy(() -> bidService.placeBid(auction2.getId(), bidderTight, oneCrore))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("credit limit");

        // Exactly the remaining 0.5cr fits and succeeds — the limit is now fully committed (2.5cr total).
        bidService.placeBid(auction2.getId(), bidderTight, halfCrore);

        // Nothing is left: even a minimal raise on auction 1 (well within its own min-increment) is
        // rejected now, since there's no shared headroom anywhere.
        assertThatThrownBy(() -> bidService.placeBid(auction.getId(), bidderTight, twoCrore.add(BigDecimal.TEN)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("credit limit");

        // Close auction 2 (bidderTight is its only/highest bidder) — its 0.5cr share must stop
        // counting immediately, freeing that headroom back up with no explicit release step.
        auctionService.closeAuctionById(auction2.getId());

        // The same raise on auction 1 that was just rejected now fits, using only the freed 0.5cr.
        bidService.placeBid(auction.getId(), bidderTight, twoCrore.add(BigDecimal.TEN));
    }

    /** Submits a bid on the shared pool; returns true if accepted, false if rejected (any BadRequestException). */
    private Future<Boolean> submitBid(ExecutorService pool, CountDownLatch ready, CountDownLatch go,
                                       User bidder, BigDecimal amount) {
        return pool.submit(() -> {
            ready.countDown();
            go.await();
            try {
                bidService.placeBid(auction.getId(), bidder, amount);
                return true;
            } catch (BadRequestException e) {
                return false;
            }
        });
    }
}
