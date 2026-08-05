package com.swipeauctions.auction.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.dispute.repository.DisputeRepository;
import com.swipeauctions.event.repository.AuctionEventRepository;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.enums.HoldStatus;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two settlement notification scenarios that live on AuctionService: the "payment complete"
 * email when a winner pays off a settlement remainder, and the reminder tick that nudges them while
 * it's still outstanding.
 */
@ExtendWith(MockitoExtension.class)
class AuctionServiceSettlementNotificationTest {

    @Mock private AuctionRepository auctionRepository;
    @Mock private com.swipeauctions.catalog.repository.ListingRepository listingRepository;
    @Mock private BidRepository bidRepository;
    @Mock private BidEligibilityHoldRepository holdRepository;
    @Mock private WalletService walletService;
    @Mock private AuctionNotificationService notificationService;
    @Mock private DisputeRepository disputeRepository;
    @Mock private AuctionEventRepository auctionEventRepository;

    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionService(auctionRepository, listingRepository, bidRepository,
                holdRepository, walletService, notificationService, disputeRepository, auctionEventRepository);
    }

    private static User user(String email) {
        User u = User.builder().email(email).build();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static Auction closedAuction(User winner, BigDecimal basePrice, BigDecimal highestBid) {
        Listing listing = Listing.builder().title("1965 Ford Mustang").build();
        Auction a = Auction.builder()
                .listing(listing).basePrice(basePrice).currentHighestBid(highestBid)
                .currentWinner(winner).status(AuctionStatus.CLOSED).settlementPaid(false)
                .winApproved(true)
                .currentEndTime(LocalDateTime.now().minusHours(1))
                .build();
        a.setId(UUID.randomUUID());
        return a;
    }

    @Test
    void completeSettlement_success_emailsWinnerWithPaidAmount() {
        User winner = user("winner@example.com");
        Auction a = closedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));
        BidEligibilityHold capturedHold = BidEligibilityHold.builder().status(HoldStatus.CAPTURED).build();

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(holdRepository.findByAuction_IdAndBidder_Id(a.getId(), winner.getId()))
                .thenReturn(Optional.of(capturedHold));
        when(walletService.captureRemainder(eq(a), eq(winner), eq(new BigDecimal("5000")))).thenReturn(true);
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));

        auctionService.completeSettlement(a.getId(), winner);

        assertThat(a.isSettlementPaid()).isTrue();
        verify(walletService).creditSaleProceeds(eq(a), any(), eq(new BigDecimal("15000")));
        verify(notificationService).settlementCompleted("winner@example.com", a.getId().toString(),
                "1965 Ford Mustang", new BigDecimal("5000"));
    }

    @Test
    void completeSettlement_insufficientBalance_sendsNoEmail() {
        User winner = user("winner@example.com");
        Auction a = closedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));
        BidEligibilityHold capturedHold = BidEligibilityHold.builder().status(HoldStatus.CAPTURED).build();

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(holdRepository.findByAuction_IdAndBidder_Id(a.getId(), winner.getId()))
                .thenReturn(Optional.of(capturedHold));
        when(walletService.captureRemainder(any(), any(), any())).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.swipeauctions.common.exception.BadRequestException.class,
                () -> auctionService.completeSettlement(a.getId(), winner));

        assertThat(a.isSettlementPaid()).isFalse();
        verify(notificationService, never()).settlementCompleted(any(), any(), any(), any());
    }

    @Test
    void completeSettlement_alreadyPaid_isIdempotent_sendsNoDuplicateEmail() {
        User winner = user("winner@example.com");
        Auction a = closedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));
        a.setSettlementPaid(true);

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));

        auctionService.completeSettlement(a.getId(), winner);

        verify(notificationService, never()).settlementCompleted(any(), any(), any(), any());
        verify(walletService, never()).captureRemainder(any(), any(), any());
    }

    @Test
    void sendSettlementReminder_stillUnpaid_emailsWinnerAndStampsReminderTime() {
        User winner = user("winner@example.com");
        Auction a = closedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(holdRepository.findByAuction_IdAndBidder_Id(a.getId(), winner.getId())).thenReturn(Optional.empty());
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));

        auctionService.sendSettlementReminder(a.getId());

        // No EMD hold on record for this bidder -> not previously captured -> full highest bid is due.
        verify(notificationService).settlementPaymentReminder("winner@example.com", a.getId().toString(),
                "1965 Ford Mustang", new BigDecimal("15000"));
        assertThat(a.getSettlementReminderSentAt()).isNotNull();
    }

    @Test
    void sendSettlementReminder_alreadyPaidInTheMeantime_skipsEmail() {
        User winner = user("winner@example.com");
        Auction a = closedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));
        a.setSettlementPaid(true);

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));

        auctionService.sendSettlementReminder(a.getId());

        verify(notificationService, never()).settlementPaymentReminder(any(), any(), any(), any());
        verify(auctionRepository, never()).save(any());
    }

    @Test
    void findAuctionsDueForSettlementReminder_delegatesToRepositoryWithComputedCutoff() {
        Auction a = closedAuction(user("winner@example.com"), new BigDecimal("10000"), new BigDecimal("15000"));
        when(auctionRepository.findDueForSettlementReminder(eq(AuctionStatus.CLOSED), any(LocalDateTime.class)))
                .thenReturn(List.of(a));

        List<UUID> due = auctionService.findAuctionsDueForSettlementReminder(24);

        assertThat(due).containsExactly(a.getId());
    }
}
