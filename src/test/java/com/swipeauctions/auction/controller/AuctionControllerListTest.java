package com.swipeauctions.auction.controller;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.bidding.service.BidService;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.repository.ListingAttributeRepository;
import com.swipeauctions.catalog.repository.ListingImageRepository;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.common.exception.UnauthorizedException;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.enums.Role;
import com.swipeauctions.event.entity.AuctionEvent;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.notification.ConfirmationLetterService;
import com.swipeauctions.settings.service.SubscriptionService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Browse/filter: the /api/auctions eventId query param only keeps auctions attached to that event. */
@ExtendWith(MockitoExtension.class)
class AuctionControllerListTest {

    @Mock private AuctionService auctionService;
    @Mock private BidService bidService;
    @Mock private WalletService walletService;
    @Mock private BidRepository bidRepository;
    @Mock private ListingImageRepository listingImageRepository;
    @Mock private ListingAttributeRepository listingAttributeRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private AuctionNotificationService notificationService;
    @Mock private LoggedInUserUtil loggedInUserUtil;
    @Mock private SubscriptionService subscriptionService;
    @Mock private ConfirmationLetterService confirmationLetterService;

    private AuctionController controller;

    @BeforeEach
    void setUp() {
        controller = new AuctionController(auctionService, bidService, walletService, bidRepository,
                listingImageRepository, listingAttributeRepository, listingRepository, notificationService,
                loggedInUserUtil, subscriptionService, confirmationLetterService);
        lenient().when(loggedInUserUtil.getCurrentUser()).thenThrow(new UnauthorizedException("anonymous"));
        // Without this, an unstubbed mock call to getCurrentAdmin() returns null instead of throwing,
        // which AuctionController.currentViewer() would read as "found an admin" — silently resolving
        // every "anonymous" call in this test as an admin viewer instead of a real anonymous one.
        lenient().when(loggedInUserUtil.getCurrentAdmin()).thenThrow(new UnauthorizedException("anonymous"));
        lenient().when(listingImageRepository.findByListing_IdOrderBySortOrderAsc(any())).thenReturn(List.of());
        lenient().when(listingAttributeRepository.findByListing_Id(any())).thenReturn(List.of());
        lenient().when(bidRepository.countByAuction_Id(any())).thenReturn(0L);
    }

    private static Auction auctionFor(AuctionEvent event) {
        User seller = User.builder().email("seller@swipeauctions.test").build();
        Category category = Category.builder().name("Vehicles").slug("vehicles").build();
        Listing listing = Listing.builder().seller(seller).category(category).title("Item")
                .condition(ItemCondition.USED).reservePrice(new BigDecimal("100.00")).build();
        Auction a = Auction.builder().listing(listing).event(event)
                .basePrice(new BigDecimal("100.00")).status(AuctionStatus.OPEN).build();
        a.setId(UUID.randomUUID());
        return a;
    }

    @Test
    void noEventIdFilter_returnsEveryAuction() {
        AuctionEvent event = AuctionEvent.builder().name("Bank Sale").build();
        event.setId(UUID.randomUUID());
        Auction inEvent = auctionFor(event);
        Auction noEvent = auctionFor(null);
        when(auctionService.list(null)).thenReturn(List.of(inEvent, noEvent));

        List<AuctionController.AuctionResponse> result = controller.list(null, null);

        assertThat(result).extracting(AuctionController.AuctionResponse::id)
                .containsExactlyInAnyOrder(inEvent.getId(), noEvent.getId());
    }

    @Test
    void eventIdFilter_keepsOnlyAuctionsInThatEvent() {
        AuctionEvent matchingEvent = AuctionEvent.builder().name("Bank Sale").build();
        matchingEvent.setId(UUID.randomUUID());
        AuctionEvent otherEvent = AuctionEvent.builder().name("Other Sale").build();
        otherEvent.setId(UUID.randomUUID());

        Auction inMatchingEvent = auctionFor(matchingEvent);
        Auction inOtherEvent = auctionFor(otherEvent);
        Auction noEvent = auctionFor(null);
        when(auctionService.list(null)).thenReturn(List.of(inMatchingEvent, inOtherEvent, noEvent));

        List<AuctionController.AuctionResponse> result = controller.list(null, matchingEvent.getId());

        assertThat(result).extracting(AuctionController.AuctionResponse::id)
                .containsExactly(inMatchingEvent.getId());
    }

    @Test
    void currentWinnerIdentity_hiddenFromAnonymousViewer_visibleToAdminViewer() {
        User winner = User.builder().email("winner@swipeauctions.test").build();
        winner.setId(UUID.randomUUID());
        Auction auction = auctionFor(null);
        auction.setCurrentWinner(winner);
        when(auctionService.list(null)).thenReturn(List.of(auction));

        List<AuctionController.AuctionResponse> anonymousResult = controller.list(null, null);
        assertThat(anonymousResult).hasSize(1);
        assertThat(anonymousResult.get(0).currentWinnerId()).isNull();
        assertThat(anonymousResult.get(0).currentWinnerEmail()).isNull();

        User admin = User.builder().role(Role.ADMIN).email("admin@swipeauctions.test").build();
        admin.setId(UUID.randomUUID());
        // doReturn(...), not when(...).thenReturn(...): getCurrentUser() is currently stubbed to
        // throw (setUp), and when(mock.method()) would invoke that throwing stub before Mockito
        // gets a chance to record the new one.
        org.mockito.Mockito.doReturn(admin).when(loggedInUserUtil).getCurrentUser();

        List<AuctionController.AuctionResponse> adminResult = controller.list(null, null);
        assertThat(adminResult).hasSize(1);
        assertThat(adminResult.get(0).currentWinnerId()).isEqualTo(winner.getId());
        assertThat(adminResult.get(0).currentWinnerEmail()).isEqualTo(winner.getEmail());
    }
}
