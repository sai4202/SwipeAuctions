package com.swipeauctions.auction.controller;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.bidding.entity.Bid;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.bidding.service.BidService;
import com.swipeauctions.catalog.entity.ListingImage;
import com.swipeauctions.catalog.repository.ListingAttributeRepository;
import com.swipeauctions.catalog.repository.ListingImageRepository;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.enums.Role;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.settings.service.SubscriptionService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Browse auctions (public); create, register-to-bid, and place bids (authenticated). */
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;
    private final BidService bidService;
    private final WalletService walletService;
    private final BidRepository bidRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingAttributeRepository listingAttributeRepository;
    private final ListingRepository listingRepository;
    private final AuctionNotificationService notificationService;
    private final LoggedInUserUtil loggedInUserUtil;
    private final SubscriptionService subscriptionService;

    @GetMapping
    public List<AuctionResponse> list(@RequestParam(required = false) AuctionStatus status,
                                       @RequestParam(required = false) UUID eventId) {
        UUID viewerId = currentUserIdOrNull();
        List<Auction> auctions = auctionService.list(status).stream()
                .filter(a -> eventId == null || (a.getEvent() != null && a.getEvent().getId().equals(eventId)))
                .toList();
        BrowseBatch batch = loadBrowseBatch(auctions, viewerId);
        return auctions.stream().map(a -> toResponse(a, viewerId, batch)).toList();
    }

    @GetMapping("/{id}")
    public AuctionResponse get(@PathVariable UUID id) {
        return toResponse(auctionService.get(id), currentUserIdOrNull());
    }

    /** Auctions the current user has won (settled/CLOSED with them as the current winner). */
    @GetMapping("/mine/won")
    public List<AuctionResponse> myWins() {
        User me = loggedInUserUtil.getCurrentUser();
        List<Auction> auctions = auctionService.listWonBy(me.getId());
        BrowseBatch batch = loadBrowseBatch(auctions, me.getId());
        return auctions.stream().map(a -> toResponse(a, me.getId(), batch)).toList();
    }

    /**
     * Everything {@link #toResponse} needs for a whole list of auctions, fetched in a handful of
     * batched round trips instead of the ~7 queries per auction (listing images, attributes, bid
     * count, "your bid", active-hold check, plus lazy-loaded category/seller) that a naive per-auction
     * lookup would cost — the difference between ~5 queries total and ~5*N for N auctions.
     */
    private record BrowseBatch(
            Map<UUID, com.swipeauctions.catalog.entity.Listing> listingsById,
            Map<UUID, List<ListingImage>> imagesByListingId,
            Map<UUID, Map<String, String>> attributesByListingId,
            Map<UUID, Long> bidCountByAuctionId,
            Map<UUID, BigDecimal> yourBidByAuctionId,
            Map<UUID, Long> yourBidCountByAuctionId,
            java.util.Set<UUID> auctionIdsWithActiveHold) {
    }

    private BrowseBatch loadBrowseBatch(List<Auction> auctions, UUID viewerId) {
        List<UUID> auctionIds = auctions.stream().map(Auction::getId).toList();
        List<UUID> listingIds = auctions.stream().map(a -> a.getListing().getId()).distinct().toList();

        Map<UUID, com.swipeauctions.catalog.entity.Listing> listingsById = listingIds.isEmpty() ? Map.of()
                : listingRepository.findAllWithCategoryAndSellerByIdIn(listingIds).stream()
                        .collect(Collectors.toMap(com.swipeauctions.catalog.entity.Listing::getId, l -> l));

        Map<UUID, List<ListingImage>> imagesByListingId = listingIds.isEmpty() ? Map.of()
                : listingImageRepository.findByListing_IdInOrderBySortOrderAsc(listingIds).stream()
                        .collect(Collectors.groupingBy(img -> img.getListing().getId()));

        Map<UUID, Map<String, String>> attributesByListingId = listingIds.isEmpty() ? Map.of()
                : listingAttributeRepository.findByListing_IdIn(listingIds).stream()
                        .collect(Collectors.groupingBy(la -> la.getListing().getId(),
                                Collectors.toMap(la -> la.getKey(), la -> la.getValue(), (x, y) -> x, LinkedHashMap::new)));

        Map<UUID, Long> bidCountByAuctionId = auctionIds.isEmpty() ? Map.of()
                : bidRepository.countByAuction_IdIn(auctionIds).stream()
                        .collect(Collectors.toMap(BidRepository.AuctionBidCount::getAuctionId, BidRepository.AuctionBidCount::getCnt));

        List<Bid> viewerBids = (viewerId == null || auctionIds.isEmpty()) ? List.of()
                : bidRepository.findByAuction_IdInAndBidder_Id(auctionIds, viewerId);
        Map<UUID, BigDecimal> yourBidByAuctionId = viewerBids.stream()
                .collect(Collectors.toMap(b -> b.getAuction().getId(), Bid::getAmount, BigDecimal::max));
        Map<UUID, Long> yourBidCountByAuctionId = viewerBids.stream()
                .collect(Collectors.groupingBy(b -> b.getAuction().getId(), Collectors.counting()));

        java.util.Set<UUID> auctionIdsWithActiveHold = (viewerId == null || auctionIds.isEmpty()) ? java.util.Set.of()
                : walletService.auctionIdsWithActiveHold(auctionIds, viewerId);

        return new BrowseBatch(listingsById, imagesByListingId, attributesByListingId,
                bidCountByAuctionId, yourBidByAuctionId, yourBidCountByAuctionId, auctionIdsWithActiveHold);
    }

    @PostMapping
    public AuctionResponse create(@Valid @RequestBody CreateAuctionRequest req) {
        User seller = loggedInUserUtil.getCurrentUser();
        Auction a = auctionService.createAuction(seller, req.listingId(), req.basePrice(),
                req.startTime(), req.endTime(), req.eventId());
        return toResponse(a, seller.getId());
    }

    /** Register to bid: places the refundable EMD (Earnest Money Deposit) hold. Dealers are exempt from the EMD, but not from KYC. */
    @PostMapping("/{id}/register")
    public RegisterResponse register(@PathVariable UUID id) {
        User bidder = loggedInUserUtil.getCurrentUser();
        if (bidder.getRole() == Role.ADMIN) {
            throw new com.swipeauctions.common.exception.BadRequestException(
                    "Admin accounts are for management only and can't register to bid.");
        }
        if (!Boolean.TRUE.equals(bidder.getKycCompleted())) {
            throw new com.swipeauctions.common.exception.BadRequestException(
                    "Complete your KYC verification before bidding.");
        }
        Auction a = auctionService.get(id);
        // Mirrors BidService.placeBid's guard — without it, a bidder can pay an EMD hold on an auction
        // that's already past its end time but hasn't been flipped to CLOSED/UNSOLD yet by the scheduler
        // (a few seconds' lag, or indefinitely if that auction's close is stuck), leaving the hold with
        // no way to ever place a bid against it.
        if (a.getStatus() != AuctionStatus.OPEN) {
            throw new com.swipeauctions.common.exception.BadRequestException("Auction is not open for bidding");
        }
        if (LocalDateTime.now().isAfter(a.getCurrentEndTime())) {
            throw new com.swipeauctions.common.exception.BadRequestException("Auction has already ended");
        }
        if (a.getListing().getSeller().getId().equals(bidder.getId())) {
            throw new com.swipeauctions.common.exception.BadRequestException("Sellers cannot register to bid on their own auction");
        }
        requireTierAccess(bidder, a);
        String message;
        if (bidder.getRole() == Role.DEALER) {
            message = "Registered to bid as a dealer — no EMD required.";
        } else {
            walletService.placeHold(bidder, a);
            message = "EMD deposited: " + a.getBasePrice() + ". Fully refundable if you don't win this auction.";
        }
        Wallet w = walletService.getWallet(bidder);
        notificationService.registeredToBid(bidder.getEmail(), a.getListing().getTitle(), a.getBasePrice());
        return new RegisterResponse(message, w.getAvailableBalance(), w.getHeldBalance());
    }

    @PostMapping("/{id}/bids")
    public BidResponse bid(@PathVariable UUID id, @Valid @RequestBody PlaceBidRequest req) {
        User bidder = loggedInUserUtil.getCurrentUser();
        if (bidder.getRole() == Role.ADMIN) {
            throw new com.swipeauctions.common.exception.BadRequestException(
                    "Admin accounts are for management only and can't place bids.");
        }
        requireTierAccess(bidder, auctionService.get(id));
        Bid bid = bidService.placeBid(id, bidder, req.amount());
        // A bid can be accepted (it beat the bidder's own last bid) without becoming the winning
        // bid — the client can no longer assume its own amount is now the auction's highest, so it
        // needs the real post-bid state back rather than inferring it from what it just sent.
        Auction updated = bid.getAuction();
        boolean leading = updated.getCurrentWinner() != null && updated.getCurrentWinner().getId().equals(bidder.getId());
        return new BidResponse(bid.getId(), id, bid.getAmount(), bid.getPlacedAt(),
                updated.getCurrentEndTime(), updated.getCurrentHighestBid(), leading);
    }

    /** Winner pays off the settlement remainder later, if it couldn't be captured in full at close time. */
    @PostMapping("/{id}/complete-payment")
    public AuctionResponse completePayment(@PathVariable UUID id) {
        User winner = loggedInUserUtil.getCurrentUser();
        Auction a = auctionService.completeSettlement(id, winner);
        return toResponse(a, winner.getId());
    }

    private void requireTierAccess(User bidder, Auction a) {
        SubscriptionTier required = a.getListing().getRequiredTier();
        if (!subscriptionService.currentTier(bidder).meetsRequirement(required)) {
            throw new com.swipeauctions.common.exception.BadRequestException(
                    "This item requires a " + required + " subscription. Upgrade your plan to access it.");
        }
    }

    /** Current user's id if a valid JWT is present, else null (browse is public). */
    private UUID currentUserIdOrNull() {
        try {
            return loggedInUserUtil.getCurrentUser().getId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private AuctionResponse toResponse(Auction a, UUID viewerId) {
        var l = a.getListing();
        List<ListingImage> images = listingImageRepository.findByListing_IdOrderBySortOrderAsc(l.getId());
        String coverImageUrl = images.stream().filter(ListingImage::isCover).findFirst()
                .or(() -> images.stream().findFirst())
                .map(ListingImage::getUrl).orElse(null);
        BigDecimal yourBid = viewerId == null ? null
                : bidRepository.findFirstByAuction_IdAndBidder_IdOrderByAmountDesc(a.getId(), viewerId)
                        .map(Bid::getAmount).orElse(null);
        Map<String, String> attributes = listingAttributeRepository.findByListing_Id(l.getId()).stream()
                .collect(Collectors.toMap(la -> la.getKey(), la -> la.getValue(),
                        (x, y) -> x, LinkedHashMap::new));
        boolean isWinner = viewerId != null && a.getCurrentWinner() != null && a.getCurrentWinner().getId().equals(viewerId);
        // Dealers skip the EMD hold entirely (see register()) but are still "registered" once they've
        // placed a bid — an active hold alone would under-report them, so also check for a bid on file.
        boolean registered = viewerId != null
                && (walletService.hasActiveHold(a.getId(), viewerId) || yourBid != null);
        Integer bidsRemaining = viewerId == null ? null : (int) Math.max(0,
                BidService.MAX_BIDS_PER_BIDDER_PER_AUCTION - bidRepository.countByAuction_IdAndBidder_Id(a.getId(), viewerId));
        return new AuctionResponse(
                a.getId(), l.getId(), l.getTitle(),
                a.getBasePrice(), a.getCurrentHighestBid(), a.getStatus(),
                a.getStartTime(), a.getCurrentEndTime(), bidRepository.countByAuction_Id(a.getId()),
                l.getCategory().getName(), l.getCategory().getId(), l.getBrand(),
                l.getCondition().name(), l.getCity(), l.getState(), l.getZip(),
                coverImageUrl, images.stream().map(ListingImage::getUrl).toList(),
                yourBid, attributes, isWinner, a.isSettlementPaid(),
                a.getEvent() != null ? a.getEvent().getId() : null,
                a.getEvent() != null ? a.getEvent().getName() : null,
                l.getSeller().getEmail(), l.isSwipeStock(), l.getRequiredTier(), registered, bidsRemaining);
    }

    /** Same shape as {@link #toResponse(Auction, UUID)} but reads from a pre-fetched {@link BrowseBatch}. */
    private AuctionResponse toResponse(Auction a, UUID viewerId, BrowseBatch batch) {
        var l = batch.listingsById().getOrDefault(a.getListing().getId(), a.getListing());
        List<ListingImage> images = batch.imagesByListingId().getOrDefault(l.getId(), List.of());
        String coverImageUrl = images.stream().filter(ListingImage::isCover).findFirst()
                .or(() -> images.stream().findFirst())
                .map(ListingImage::getUrl).orElse(null);
        BigDecimal yourBid = batch.yourBidByAuctionId().get(a.getId());
        Map<String, String> attributes = batch.attributesByListingId().getOrDefault(l.getId(), Map.of());
        boolean isWinner = viewerId != null && a.getCurrentWinner() != null && a.getCurrentWinner().getId().equals(viewerId);
        // Dealers skip the EMD hold entirely (see register()) but are still "registered" once they've
        // placed a bid — an active hold alone would under-report them, so also check for a bid on file.
        boolean registered = viewerId != null
                && (batch.auctionIdsWithActiveHold().contains(a.getId()) || yourBid != null);
        Integer bidsRemaining = viewerId == null ? null : (int) Math.max(0,
                BidService.MAX_BIDS_PER_BIDDER_PER_AUCTION - batch.yourBidCountByAuctionId().getOrDefault(a.getId(), 0L));
        return new AuctionResponse(
                a.getId(), l.getId(), l.getTitle(),
                a.getBasePrice(), a.getCurrentHighestBid(), a.getStatus(),
                a.getStartTime(), a.getCurrentEndTime(), batch.bidCountByAuctionId().getOrDefault(a.getId(), 0L),
                l.getCategory().getName(), l.getCategory().getId(), l.getBrand(),
                l.getCondition().name(), l.getCity(), l.getState(), l.getZip(),
                coverImageUrl, images.stream().map(ListingImage::getUrl).toList(),
                yourBid, attributes, isWinner, a.isSettlementPaid(),
                a.getEvent() != null ? a.getEvent().getId() : null,
                a.getEvent() != null ? a.getEvent().getName() : null,
                l.getSeller().getEmail(), l.isSwipeStock(), l.getRequiredTier(), registered, bidsRemaining);
    }

    public record CreateAuctionRequest(
            @NotNull UUID listingId,
            BigDecimal basePrice,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            UUID eventId) {}

    public record AuctionResponse(
            UUID id, UUID listingId, String title, BigDecimal basePrice, BigDecimal currentHighestBid,
            AuctionStatus status, LocalDateTime startTime, LocalDateTime currentEndTime, long bidCount,
            String categoryName, UUID categoryId, String brand, String condition, String city, String state,
            String zip, String coverImageUrl, List<String> images, BigDecimal yourBid,
            Map<String, String> attributes, boolean isWinner, boolean settlementPaid,
            UUID eventId, String eventName, String sellerEmail, boolean swipeStock,
            SubscriptionTier requiredTier, boolean registered, Integer bidsRemaining) {}

    public record PlaceBidRequest(@NotNull BigDecimal amount) {}

    public record BidResponse(UUID bidId, UUID auctionId, BigDecimal amount, LocalDateTime placedAt,
                              LocalDateTime currentEndTime, BigDecimal currentHighestBid, boolean leading) {}

    public record RegisterResponse(String message, BigDecimal availableBalance, BigDecimal heldBalance) {}
}
