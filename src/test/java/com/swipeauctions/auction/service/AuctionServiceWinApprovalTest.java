package com.swipeauctions.auction.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.dispute.repository.DisputeRepository;
import com.swipeauctions.event.repository.AuctionEventRepository;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** approveWin/rejectWin, and completeSettlement's new "win must be approved first" guard. */
@ExtendWith(MockitoExtension.class)
class AuctionServiceWinApprovalTest {

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

    private static Auction closedUnapprovedAuction(User winner, BigDecimal basePrice, BigDecimal highestBid) {
        Listing listing = Listing.builder().title("1965 Ford Mustang").build();
        Auction a = Auction.builder()
                .listing(listing).basePrice(basePrice).currentHighestBid(highestBid)
                .currentWinner(winner).status(AuctionStatus.CLOSED).settlementPaid(false)
                .winApproved(false)
                .currentEndTime(LocalDateTime.now().minusHours(1))
                .build();
        a.setId(UUID.randomUUID());
        return a;
    }

    @Test
    void completeSettlement_notYetApproved_isRejected() {
        User winner = user("winner@example.com");
        Auction a = closedUnapprovedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));
        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> auctionService.completeSettlement(a.getId(), winner))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("approved");

        assertThat(a.isSettlementPaid()).isFalse();
        verify(walletService, never()).captureRemainder(any(), any(), any());
    }

    @Test
    void approveWin_autoSettlesWhenFundsAllow() {
        User winner = user("winner@example.com");
        Auction a = closedUnapprovedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(walletService.captureRemainder(eq(a), eq(winner), any(BigDecimal.class))).thenReturn(true);
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));

        Auction result = auctionService.approveWin(a.getId());

        assertThat(result.isWinApproved()).isTrue();
        assertThat(result.getWinApprovedAt()).isNotNull();
        assertThat(result.isSettlementPaid()).isTrue();
        verify(walletService).creditSaleProceeds(eq(a), any(), eq(new BigDecimal("15000")));
        verify(notificationService).winApproved(eq("winner@example.com"), eq(a.getId().toString()),
                eq("1965 Ford Mustang"), any(BigDecimal.class), eq(true));
    }

    @Test
    void approveWin_leavesUnpaidWhenFundsInsufficient() {
        User winner = user("winner@example.com");
        Auction a = closedUnapprovedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(walletService.captureRemainder(eq(a), eq(winner), any(BigDecimal.class))).thenReturn(false);
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));

        Auction result = auctionService.approveWin(a.getId());

        assertThat(result.isWinApproved()).isTrue();
        assertThat(result.isSettlementPaid()).isFalse();
        verify(walletService, never()).creditSaleProceeds(any(), any(), any());
        verify(notificationService).winApproved(eq("winner@example.com"), eq(a.getId().toString()),
                eq("1965 Ford Mustang"), any(BigDecimal.class), eq(false));
    }

    @Test
    void approveWin_isIdempotent() {
        User winner = user("winner@example.com");
        Auction a = closedUnapprovedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));
        a.setWinApproved(true);

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));

        Auction result = auctionService.approveWin(a.getId());

        assertThat(result).isSameAs(a);
        verify(walletService, never()).captureRemainder(any(), any(), any());
        verify(notificationService, never()).winApproved(any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void rejectWin_refundsDepositAndMarksUnsold() {
        User winner = user("winner@example.com");
        Auction a = closedUnapprovedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));
        com.swipeauctions.wallet.entity.BidEligibilityHold hold =
                com.swipeauctions.wallet.entity.BidEligibilityHold.builder().build();

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(holdRepository.findByAuction_IdAndBidder_Id(a.getId(), winner.getId())).thenReturn(Optional.of(hold));
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));

        Auction result = auctionService.rejectWin(a.getId(), "Suspected fraudulent bid");

        assertThat(result.getStatus()).isEqualTo(AuctionStatus.UNSOLD);
        assertThat(result.getCurrentWinner()).isNull();
        verify(walletService).refundCapturedHold(a, winner);
        verify(notificationService).winRejected("winner@example.com", a.getId().toString(),
                "1965 Ford Mustang", "Suspected fraudulent bid");
    }

    @Test
    void rejectWin_dealerWinnerWithNoHold_skipsRefundButStillRejects() {
        User winner = user("dealer@example.com");
        Auction a = closedUnapprovedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
        when(holdRepository.findByAuction_IdAndBidder_Id(a.getId(), winner.getId())).thenReturn(Optional.empty());
        when(auctionRepository.save(any(Auction.class))).thenAnswer(inv -> inv.getArgument(0));

        Auction result = auctionService.rejectWin(a.getId(), "Reserve not actually met");

        assertThat(result.getStatus()).isEqualTo(AuctionStatus.UNSOLD);
        verify(walletService, never()).refundCapturedHold(any(), any());
        verify(notificationService).winRejected("dealer@example.com", a.getId().toString(),
                "1965 Ford Mustang", "Reserve not actually met");
    }

    @Test
    void rejectWin_alreadyApproved_isRejected() {
        User winner = user("winner@example.com");
        Auction a = closedUnapprovedAuction(winner, new BigDecimal("10000"), new BigDecimal("15000"));
        a.setWinApproved(true);

        when(auctionRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> auctionService.rejectWin(a.getId(), "too late"))
                .isInstanceOf(BadRequestException.class);

        verify(walletService, never()).refundCapturedHold(any(), any());
    }
}
