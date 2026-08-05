package com.swipeauctions.auction.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.dispute.repository.DisputeRepository;
import com.swipeauctions.event.repository.AuctionEventRepository;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Browse/filter: AuctionService.list dispatches to findByStatus when filtered, findAll otherwise. */
@ExtendWith(MockitoExtension.class)
class AuctionServiceListTest {

    @Mock private AuctionRepository auctionRepository;
    @Mock private ListingRepository listingRepository;
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

    @Test
    void noStatusFilter_returnsAllAuctions() {
        Auction a = Auction.builder().status(AuctionStatus.OPEN).build();
        Auction b = Auction.builder().status(AuctionStatus.CLOSED).build();
        when(auctionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a, b));

        List<Auction> result = auctionService.list(null);

        assertThat(result).containsExactly(a, b);
        verify(auctionRepository, never()).findByStatusOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void statusFilter_delegatesToFindByStatus() {
        Auction open = Auction.builder().status(AuctionStatus.OPEN).build();
        when(auctionRepository.findByStatusOrderByCreatedAtDesc(AuctionStatus.OPEN)).thenReturn(List.of(open));

        List<Auction> result = auctionService.list(AuctionStatus.OPEN);

        assertThat(result).containsExactly(open);
        verify(auctionRepository, never()).findAllByOrderByCreatedAtDesc();
    }
}
