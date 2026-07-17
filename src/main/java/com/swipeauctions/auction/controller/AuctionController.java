package com.swipeauctions.auction.controller;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.bidding.entity.Bid;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.bidding.service.BidService;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Browse auctions (public); create, register-to-bid, and place bids (authenticated). */
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;
    private final BidService bidService;
    private final WalletService walletService;
    private final BidRepository bidRepository;
    private final LoggedInUserUtil loggedInUserUtil;

    @GetMapping
    public List<AuctionResponse> list(@RequestParam(required = false) AuctionStatus status) {
        return auctionService.list(status).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public AuctionResponse get(@PathVariable UUID id) {
        return toResponse(auctionService.get(id));
    }

    @PostMapping
    public AuctionResponse create(@Valid @RequestBody CreateAuctionRequest req) {
        User seller = loggedInUserUtil.getCurrentUser();
        Auction a = auctionService.createAuction(seller, req.listingId(), req.basePrice(),
                req.startTime(), req.endTime());
        return toResponse(a);
    }

    /** Register to bid: places the refundable wallet deposit hold (the bidding gate). */
    @PostMapping("/{id}/register")
    public RegisterResponse register(@PathVariable UUID id) {
        User bidder = loggedInUserUtil.getCurrentUser();
        Auction a = auctionService.get(id);
        walletService.placeHold(bidder, a);
        Wallet w = walletService.getWallet(bidder);
        return new RegisterResponse(
                "Registered to bid. Deposit held: " + a.getBasePrice(),
                w.getAvailableBalance(), w.getHeldBalance());
    }

    @PostMapping("/{id}/bids")
    public BidResponse bid(@PathVariable UUID id, @Valid @RequestBody PlaceBidRequest req) {
        User bidder = loggedInUserUtil.getCurrentUser();
        Bid bid = bidService.placeBid(id, bidder, req.amount());
        return new BidResponse(bid.getId(), id, bid.getAmount(), bid.getPlacedAt(),
                bid.getAuction().getCurrentEndTime());
    }

    private AuctionResponse toResponse(Auction a) {
        return new AuctionResponse(
                a.getId(), a.getListing().getId(), a.getListing().getTitle(),
                a.getBasePrice(), a.getCurrentHighestBid(), a.getStatus(),
                a.getStartTime(), a.getCurrentEndTime(), bidRepository.countByAuction_Id(a.getId()));
    }

    public record CreateAuctionRequest(
            @NotNull UUID listingId,
            BigDecimal basePrice,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime) {}

    public record AuctionResponse(
            UUID id, UUID listingId, String title, BigDecimal basePrice, BigDecimal currentHighestBid,
            AuctionStatus status, LocalDateTime startTime, LocalDateTime currentEndTime, long bidCount) {}

    public record PlaceBidRequest(@NotNull BigDecimal amount) {}

    public record BidResponse(UUID bidId, UUID auctionId, BigDecimal amount, LocalDateTime placedAt,
                              LocalDateTime currentEndTime) {}

    public record RegisterResponse(String message, BigDecimal availableBalance, BigDecimal heldBalance) {}
}
