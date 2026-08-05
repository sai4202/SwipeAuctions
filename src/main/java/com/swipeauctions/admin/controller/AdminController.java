package com.swipeauctions.admin.controller;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.enums.AuditAction;
import com.swipeauctions.admin.service.AdminAuditLogService;
import com.swipeauctions.admin.service.AdminUserService;
import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.bidding.entity.AuctionDisqualification;
import com.swipeauctions.bidding.entity.Bid;
import com.swipeauctions.bidding.repository.AuctionDisqualificationRepository;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.bidding.service.BidService;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.CategoryAttributeDef;
import com.swipeauctions.catalog.entity.CategoryBanner;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.AttributeValueType;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.catalog.repository.CategoryBannerRepository;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.catalog.service.CatalogService;
import com.swipeauctions.storage.service.StorageProvider;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.common.response.PageResponse;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.dispute.controller.DisputeController;
import com.swipeauctions.dispute.entity.Dispute;
import com.swipeauctions.dispute.enums.DisputeStatus;
import com.swipeauctions.dispute.repository.DisputeRepository;
import com.swipeauctions.dispute.service.DisputeService;
import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.enums.Role;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.user.entity.KycVerification;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.user.service.KycService;
import com.swipeauctions.payment.RazorpayConfig;
import com.swipeauctions.referral.entity.Referral;
import com.swipeauctions.referral.repository.ReferralRepository;
import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.entity.PaymentOrder;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.entity.WalletTransaction;
import com.swipeauctions.wallet.entity.WalletWithdrawal;
import com.swipeauctions.wallet.enums.HoldStatus;
import com.swipeauctions.wallet.enums.TopUpStatus;
import com.swipeauctions.wallet.enums.WalletTxnType;
import com.swipeauctions.wallet.enums.WithdrawalStatus;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.repository.PaymentOrderRepository;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import com.swipeauctions.wallet.repository.WalletWithdrawalRepository;
import com.swipeauctions.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Admin dashboard: user management, listing/auction oversight, force-close, disputes, stats. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AuctionService auctionService;
    private final AuctionRepository auctionRepository;
    private final ListingRepository listingRepository;
    private final BidRepository bidRepository;
    private final BidService bidService;
    private final AuctionDisqualificationRepository disqualificationRepository;
    private final DisputeService disputeService;
    private final DisputeRepository disputeRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CatalogService catalogService;
    private final KycService kycService;
    private final WalletService walletService;
    private final BidEligibilityHoldRepository holdRepository;
    private final WalletWithdrawalRepository walletWithdrawalRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final RazorpayConfig razorpayConfig;
    private final CategoryBannerRepository categoryBannerRepository;
    private final StorageProvider storageProvider;
    private final ReferralRepository referralRepository;
    private final LoggedInUserUtil loggedInUserUtil;
    private final AdminAuditLogService auditLogService;

    // ---- Users ----

    @GetMapping("/users")
    public PageResponse<UserResponse> users(@RequestParam(required = false) String search,
                                             @RequestParam(required = false) Role role,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(adminUserService.listUsers(search, role, active, pageable(page, size, "createdAt")),
                this::toUser);
    }

    @GetMapping("/users/{id}")
    public UserResponse user(@PathVariable UUID id) {
        return toUser(adminUserService.getUser(id));
    }

    @PostMapping("/users/{id}/suspend")
    public UserResponse suspend(@PathVariable UUID id) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        User user = adminUserService.suspend(id);
        auditLogService.record(admin, AuditAction.USER_SUSPENDED, "User", user.getId().toString(),
                "Suspended user " + user.getEmail());
        return toUser(user);
    }

    @PostMapping("/users/{id}/reactivate")
    public UserResponse reactivate(@PathVariable UUID id) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        User user = adminUserService.reactivate(id);
        auditLogService.record(admin, AuditAction.USER_REACTIVATED, "User", user.getId().toString(),
                "Reactivated user " + user.getEmail());
        return toUser(user);
    }

    /** Active (unresolved) EMD holds for a user — the "locked" amounts an admin can refund/release. */
    @GetMapping("/users/{id}/holds")
    public List<HoldResponse> userHolds(@PathVariable UUID id) {
        User user = adminUserService.getUser(id);
        return holdRepository.findByBidder_IdAndStatus(user.getId(), HoldStatus.ACTIVE).stream()
                .map(AdminController::toHold).toList();
    }

    /** General manual wallet correction tool — disputes, mistaken bids, or any other one-off fix
     *  that doesn't fit the narrower EMD-hold-release/dispute-resolution paths. Always audit-logged
     *  with the admin-supplied reason; the user gets a notification either way. */
    @PostMapping("/users/{id}/wallet/adjust")
    public ReleaseHoldResponse adjustWallet(@PathVariable UUID id, @Valid @RequestBody AdjustWalletRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        User user = adminUserService.getUser(id);
        Wallet w = walletService.adminAdjust(user, req.amount(), req.credit(), req.reason());
        auditLogService.record(admin, AuditAction.WALLET_ADJUSTED, "User", user.getId().toString(),
                (req.credit() ? "Credited " : "Debited ") + req.amount() + " " + (req.credit() ? "to" : "from")
                        + " " + user.getEmail() + "'s wallet — reason: " + req.reason());
        return new ReleaseHoldResponse(w.getAvailableBalance(), w.getHeldBalance(), walletService.creditLimitFor(w.getAvailableBalance()));
    }

    public record AdjustWalletRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive BigDecimal amount,
                                       boolean credit, @NotBlank String reason) {}

    /** Everything this user has bid on, one row per auction (their own best bid on it) — the "how
     *  many items is he bidding" detail view, newest activity first. */
    @GetMapping("/users/{id}/bids")
    public List<UserBidResponse> userBids(@PathVariable UUID id) {
        User user = adminUserService.getUser(id);
        java.util.Map<UUID, Bid> bestPerAuction = new java.util.LinkedHashMap<>();
        for (Bid b : bidRepository.findByBidder_Id(user.getId())) {
            bestPerAuction.merge(b.getAuction().getId(), b,
                    (existing, candidate) -> candidate.getAmount().compareTo(existing.getAmount()) > 0 ? candidate : existing);
        }
        return bestPerAuction.values().stream()
                .sorted(java.util.Comparator.comparing(Bid::getPlacedAt).reversed())
                .map(AdminController::toUserBid).toList();
    }

    /** Force-release a stuck/locked EMD hold back to the bidder's available balance. */
    @PostMapping("/holds/{holdId}/release")
    public ReleaseHoldResponse releaseHold(@PathVariable UUID holdId) {
        BidEligibilityHold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found"));
        // This is for clearing a genuinely stuck hold (auction already decided one way or another)
        // — releasing on a still-live auction would strip a bidder's eligibility mid-auction without
        // also unregistering them, an inconsistent state. See Findings_pendings.md (Low/Informational).
        AuctionStatus auctionStatus = hold.getAuction().getStatus();
        if (auctionStatus == AuctionStatus.SCHEDULED || auctionStatus == AuctionStatus.OPEN) {
            throw new BadRequestException(
                    "Cannot release an EMD hold while its auction is still " + auctionStatus + " — wait until it closes.");
        }
        walletService.releaseHold(hold.getAuction(), hold.getBidder());
        Wallet w = walletService.getWallet(hold.getBidder());
        auditLogService.record(loggedInUserUtil.getCurrentAdmin(), AuditAction.HOLD_RELEASED, "Hold", holdId.toString(),
                "Released EMD hold for " + hold.getBidder().getEmail() + " on \"" + hold.getAuction().getListing().getTitle() + "\"");
        return new ReleaseHoldResponse(w.getAvailableBalance(), w.getHeldBalance(), walletService.creditLimitFor(w.getAvailableBalance()));
    }

    // ---- Wallet: credit/holds overview, payments (top-ups), withdrawals, transactions ----
    // Adapted from the reference admin panel's Investments/Payments/Withdrawals/Transactions
    // sections onto SwipeAuctions' actual wallet model — there's no fixed-return investment
    // product here, just the real EMD-hold / Razorpay top-up / Razorpay payout machinery that
    // already powers bidding and the wallet page.

    /** Admin-wide view of every currently-locked EMD hold — "Credit & Holds Overview". */
    @GetMapping("/holds")
    public PageResponse<AdminHoldResponse> allHolds(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(holdRepository.findByStatus(HoldStatus.ACTIVE, pageable(page, size, "createdAt")),
                AdminController::toAdminHold);
    }

    /** Wallet top-up (Razorpay Order) queue — the "Payments" section, real deposit orders only. */
    @GetMapping("/payments")
    public PageResponse<PaymentOrderResponse> payments(@RequestParam(required = false) TopUpStatus status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        var pageable = pageable(page, size, "createdAt");
        var result = status != null ? paymentOrderRepository.findByStatus(status, pageable) : paymentOrderRepository.findAll(pageable);
        return PageResponse.of(result, AdminController::toPaymentOrder);
    }

    /** Whether Razorpay Payments/Payouts are actually configured — shown instead of a fake "manage
     *  settlement bank account" form, since payouts settle from the single RazorpayX account set via
     *  env var, not from anything admin-editable in this app. */
    @GetMapping("/payments/settlement-status")
    public SettlementStatusResponse settlementStatus() {
        return new SettlementStatusResponse(razorpayConfig.isEnabled(), razorpayConfig.payoutsEnabled());
    }

    /** Seller payout (Razorpay Payout) monitoring queue — the "Withdrawals" section. Payouts are
     *  submitted to Razorpay synchronously when the seller requests one (see WalletController#withdraw);
     *  there is no admin-approval step in the real money flow, so this is read-only status, not an
     *  approve/reject queue. */
    @GetMapping("/withdrawals")
    public PageResponse<WithdrawalResponse> withdrawals(@RequestParam(required = false) WithdrawalStatus status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        var pageable = pageable(page, size, "createdAt");
        var result = status != null ? walletWithdrawalRepository.findByStatus(status, pageable) : walletWithdrawalRepository.findAll(pageable);
        return PageResponse.of(result, AdminController::toWithdrawal);
    }

    /** Admin-wide wallet ledger (every user, not just one) — the "Transactions" section. */
    @GetMapping("/transactions")
    public PageResponse<AdminTransactionResponse> transactions(@RequestParam(required = false) WalletTxnType type,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        var pageable = pageable(page, size, "createdAt");
        var result = type != null ? walletTransactionRepository.findByType(type, pageable) : walletTransactionRepository.findAll(pageable);
        return PageResponse.of(result, AdminController::toAdminTransaction);
    }

    // ---- Listings / auctions ----

    @GetMapping("/listings")
    public PageResponse<ListingResponse> listings(@RequestParam(required = false) ListingStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = pageable(page, size, "createdAt");
        var result = status != null ? listingRepository.findByStatus(status, pageable) : listingRepository.findAll(pageable);
        return PageResponse.of(result, AdminController::toListing);
    }

    @GetMapping("/auctions")
    public PageResponse<AuctionResponse> auctions(@RequestParam(required = false) AuctionStatus status,
                                                    @RequestParam(required = false) Boolean hasBids,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = pageable(page, size, "startTime");
        var result = status != null && Boolean.TRUE.equals(hasBids) ? auctionRepository.findByStatusAndHasBids(status, pageable)
                : status != null ? auctionRepository.findByStatus(status, pageable)
                : auctionRepository.findAll(pageable);
        return PageResponse.of(result, this::toAuction);
    }

    /** All bidders currently on this auction, one row per bidder (their own best bid), ranked by
     *  that bid amount — the "See Bidders" button on the Auctions tab, since showing this inline
     *  for every row at once would be unreadably dense. */
    @GetMapping("/auctions/{id}/bidders")
    public List<AuctionBidderResponse> auctionBidders(@PathVariable UUID id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auction not found"));
        java.util.Map<UUID, Bid> bestPerBidder = new java.util.LinkedHashMap<>();
        java.util.Map<UUID, Long> countPerBidder = new java.util.HashMap<>();
        for (Bid b : bidRepository.findByAuction_IdOrderByAmountDesc(id)) {
            UUID bidderId = b.getBidder().getId();
            bestPerBidder.merge(bidderId, b,
                    (existing, candidate) -> candidate.getAmount().compareTo(existing.getAmount()) > 0 ? candidate : existing);
            countPerBidder.merge(bidderId, 1L, Long::sum);
        }
        java.util.Set<UUID> disqualified = disqualificationRepository.findByAuction_Id(id).stream()
                .map(d -> d.getBidder().getId()).collect(java.util.stream.Collectors.toSet());
        User winner = auction.getCurrentWinner();
        return bestPerBidder.values().stream()
                .sorted(java.util.Comparator.comparing(Bid::getAmount).reversed())
                .map(b -> new AuctionBidderResponse(
                        b.getBidder().getId(), b.getBidder().getEmail(), b.getId(), b.getAmount(),
                        countPerBidder.get(b.getBidder().getId()), b.getPlacedAt(),
                        winner != null && winner.getId().equals(b.getBidder().getId()),
                        disqualified.contains(b.getBidder().getId())))
                .toList();
    }

    /** Admin correction for a mistaken/disputed bid — overwrites the bid's amount directly and
     *  recomputes the auction's leader. See BidService#adminEditBid for the OPEN-only restriction. */
    @PutMapping("/bids/{bidId}")
    public void editBid(@PathVariable UUID bidId, @Valid @RequestBody EditBidRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        Bid before = bidRepository.findById(bidId).orElseThrow(() -> new ResourceNotFoundException("Bid not found"));
        BigDecimal oldAmount = before.getAmount();
        Bid updated = bidService.adminEditBid(bidId, req.amount());
        auditLogService.record(admin, AuditAction.BID_AMOUNT_MODIFIED, "Bid", bidId.toString(),
                "Changed " + updated.getBidder().getEmail() + "'s bid on \"" + updated.getAuction().getListing().getTitle()
                        + "\" from " + oldAmount + " to " + updated.getAmount() + " — reason: " + req.reason());
    }

    /** Bans a bidder from one specific auction (lighter than the account-wide suspend) — releases
     *  their EMD hold on it if any, excludes them from this auction's ranking. */
    @PostMapping("/auctions/{auctionId}/bidders/{bidderId}/disqualify")
    public void disqualifyBidder(@PathVariable UUID auctionId, @PathVariable UUID bidderId,
                                  @Valid @RequestBody DisqualifyBidderRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        bidService.disqualifyBidder(auctionId, bidderId, admin, req.reason());
        auditLogService.record(admin, AuditAction.BIDDER_DISQUALIFIED, "Auction", auctionId.toString(),
                "Disqualified bidder " + bidderId + " from an auction — reason: " + req.reason());
    }

    @DeleteMapping("/auctions/{auctionId}/bidders/{bidderId}/disqualify")
    public void reinstateBidder(@PathVariable UUID auctionId, @PathVariable UUID bidderId) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        bidService.reinstateBidder(auctionId, bidderId);
        auditLogService.record(admin, AuditAction.BIDDER_REINSTATED, "Auction", auctionId.toString(),
                "Reinstated bidder " + bidderId + " on an auction");
    }

    public record EditBidRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive BigDecimal amount,
                                  @NotBlank String reason) {}

    public record DisqualifyBidderRequest(@NotBlank String reason) {}

    @PostMapping("/auctions/{id}/force-close")
    public AuctionResponse forceClose(@PathVariable UUID id) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        Auction a = auctionService.forceClose(id);
        auditLogService.record(admin, AuditAction.AUCTION_FORCE_CLOSED, "Auction", a.getId().toString(),
                "Force-closed \"" + a.getListing().getTitle() + "\"");
        return toAuction(a);
    }

    @PatchMapping("/auctions/{id}")
    public AuctionResponse updateAuction(@PathVariable UUID id, @Valid @RequestBody UpdateAuctionRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        Auction a = auctionService.adminUpdate(id, req.title(), req.basePrice(), req.startTime(), req.endTime());
        auditLogService.record(admin, AuditAction.AUCTION_MODIFIED, "Auction", a.getId().toString(),
                "Modified auction \"" + a.getListing().getTitle() + "\" (title/base price/timing)");
        return toAuction(a);
    }

    /** Unblocks the winner's settlement payment — also auto-settles immediately if their wallet
     *  balance already covers the remainder. See AuctionService#approveWin. */
    @PostMapping("/auctions/{id}/approve-win")
    public AuctionResponse approveWin(@PathVariable UUID id) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        Auction a = auctionService.approveWin(id);
        auditLogService.record(admin, AuditAction.AUCTION_WIN_APPROVED, "Auction", a.getId().toString(),
                "Approved the win on \"" + a.getListing().getTitle() + "\"");
        return toAuction(a);
    }

    /** Cancels an unapproved win — the auction goes UNSOLD and the winner's deposit is refunded.
     *  See AuctionService#rejectWin. */
    @PostMapping("/auctions/{id}/reject-win")
    public AuctionResponse rejectWin(@PathVariable UUID id, @Valid @RequestBody RejectWinRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        Auction a = auctionService.rejectWin(id, req.reason());
        auditLogService.record(admin, AuditAction.AUCTION_WIN_REJECTED, "Auction", a.getId().toString(),
                "Rejected the win on \"" + a.getListing().getTitle() + "\" — reason: " + req.reason());
        return toAuction(a);
    }

    public record RejectWinRequest(@NotBlank String reason) {}

    @PatchMapping("/listings/{id}/required-tier")
    public ListingResponse updateRequiredTier(@PathVariable UUID id, @Valid @RequestBody UpdateRequiredTierRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        listing.setRequiredTier(req.requiredTier());
        Listing saved = listingRepository.save(listing);
        auditLogService.record(admin, AuditAction.LISTING_REQUIRED_TIER_CHANGED, "Listing", saved.getId().toString(),
                "Set required tier of \"" + saved.getTitle() + "\" to " + req.requiredTier());
        return toListing(saved);
    }

    /** Admin correction tool, not part of the creation flow — deliberately doesn't re-run
     *  requireVehicleDetails-style validation. Existing ListingAttribute rows are left as-is; if
     *  they no longer fit the new category, DetailTabs/ItemDetailStrip already render unrecognized
     *  keys gracefully via their existing fallback paths rather than breaking. */
    @PatchMapping("/listings/{id}/category")
    public ListingResponse updateListingCategory(@PathVariable UUID id, @Valid @RequestBody UpdateListingCategoryRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        Category category = catalogService.listCategories().stream()
                .filter(c -> c.getId().equals(req.categoryId())).findFirst()
                .orElseThrow(() -> new BadRequestException("Category not found"));
        String oldCategoryName = listing.getCategory().getName();
        listing.setCategory(category);
        Listing saved = listingRepository.save(listing);
        auditLogService.record(admin, AuditAction.LISTING_CATEGORY_CHANGED, "Listing", saved.getId().toString(),
                "Recategorized \"" + saved.getTitle() + "\" from " + oldCategoryName + " to " + category.getName());
        return toListing(saved);
    }

    // ---- Disputes ----

    @GetMapping("/disputes")
    public PageResponse<DisputeController.DisputeResponse> disputes(@RequestParam(required = false) DisputeStatus status,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(disputeService.list(status, pageable(page, size, "createdAt")), DisputeController::toResponse);
    }

    @GetMapping("/disputes/{id}")
    public DisputeController.DisputeResponse dispute(@PathVariable UUID id) {
        return DisputeController.toResponse(disputeService.get(id));
    }

    @PostMapping("/disputes/{id}/resolve")
    public DisputeController.DisputeResponse resolveDispute(@PathVariable UUID id,
                                                              @Valid @RequestBody ResolveDisputeRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        boolean refundBuyer = Boolean.TRUE.equals(req.refundBuyer());
        Dispute d = disputeService.resolve(id, req.adminNotes(), refundBuyer);
        auditLogService.record(admin, AuditAction.DISPUTE_RESOLVED, "Dispute", d.getId().toString(),
                "Resolved dispute on \"" + d.getAuction().getListing().getTitle() + "\" — "
                        + (refundBuyer ? "refunded buyer" : "released to seller"));
        return DisputeController.toResponse(d);
    }

    // ---- Categories ----

    /** Any admin-created category becomes selectable immediately wherever categories are picked
     *  (browse filters, the seller listing form) — no schema change, category rows are just data. */
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.listCategories().stream().map(AdminController::toCategory).toList();
    }

    @PostMapping("/categories")
    public CategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        Category c = catalogService.createCategory(req.name(), req.slug(), req.parentId());
        auditLogService.record(admin, AuditAction.CATEGORY_CREATED, "Category", c.getId().toString(),
                "Created category \"" + c.getName() + "\"");
        return toCategory(c);
    }

    @GetMapping("/categories/{id}/attributes")
    public List<CategoryAttributeResponse> categoryAttributes(@PathVariable UUID id) {
        return catalogService.listCategoryAttributes(id).stream().map(AdminController::toCategoryAttribute).toList();
    }

    @PostMapping("/categories/{id}/attributes")
    public CategoryAttributeResponse addCategoryAttribute(@PathVariable UUID id,
                                                           @Valid @RequestBody CreateCategoryAttributeRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        CategoryAttributeDef def = catalogService.addCategoryAttribute(id, req.key(), req.label(),
                req.valueType(), req.filterable() == null || req.filterable(), req.sortOrder() != null ? req.sortOrder() : 0);
        auditLogService.record(admin, AuditAction.CATEGORY_ATTRIBUTE_ADDED, "Category", id.toString(),
                "Added attribute \"" + def.getLabel() + "\" to category");
        return toCategoryAttribute(def);
    }

    // ---- Banners ----

    @GetMapping("/banners")
    public List<AdminBannerResponse> banners() {
        return categoryBannerRepository.findAllByOrderBySortOrderAsc().stream()
                .map(AdminController::toAdminBanner).toList();
    }

    @PostMapping(value = "/banners", consumes = "multipart/form-data")
    public AdminBannerResponse createBanner(@RequestParam MultipartFile image,
                                             @RequestParam(required = false) UUID categoryId,
                                             @RequestParam(required = false) String linkUrl,
                                             @RequestParam(required = false) String title,
                                             @RequestParam(defaultValue = "0") int sortOrder) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        String url = storageProvider.store(image, "banners");
        Category category = categoryId != null ? catalogService.listCategories().stream()
                .filter(c -> c.getId().equals(categoryId)).findFirst()
                .orElseThrow(() -> new BadRequestException("Category not found")) : null;
        CategoryBanner banner = categoryBannerRepository.save(CategoryBanner.builder()
                .category(category).imageUrl(url).linkUrl(linkUrl).title(title).sortOrder(sortOrder).active(true).build());
        auditLogService.record(admin, AuditAction.BANNER_CREATED, "Banner", banner.getId().toString(),
                "Added banner" + (category != null ? " for category \"" + category.getName() + "\"" : " (platform-wide)"));
        return toAdminBanner(banner);
    }

    @PatchMapping("/banners/{id}/active")
    public AdminBannerResponse toggleBannerActive(@PathVariable UUID id, @RequestBody ToggleActiveRequest req) {
        CategoryBanner banner = categoryBannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found"));
        banner.setActive(req.active());
        return toAdminBanner(categoryBannerRepository.save(banner));
    }

    @DeleteMapping("/banners/{id}")
    public void deleteBanner(@PathVariable UUID id) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        CategoryBanner banner = categoryBannerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Banner not found"));
        categoryBannerRepository.delete(banner);
        auditLogService.record(admin, AuditAction.BANNER_REMOVED, "Banner", id.toString(), "Removed banner");
    }

    static AdminBannerResponse toAdminBanner(CategoryBanner b) {
        return new AdminBannerResponse(b.getId(), b.getCategory() != null ? b.getCategory().getId() : null,
                b.getCategory() != null ? b.getCategory().getName() : null, b.getImageUrl(), b.getLinkUrl(),
                b.getTitle(), b.getSortOrder(), b.isActive(), b.getCreatedAt(), b.getUpdatedAt());
    }

    public record AdminBannerResponse(UUID id, UUID categoryId, String categoryName, String imageUrl, String linkUrl,
                                      String title, int sortOrder, boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ToggleActiveRequest(boolean active) {}

    // ---- Referrals ----

    /** Reference site's "Referral Dashboard" — who's referring whom via the invite-link mechanism
     *  in ReferralController, admin-wide, plus how many of those turned into a bonus-paying
     *  successful referral and how much has been paid out. */
    @GetMapping("/referrals")
    public ReferralDashboardResponse referrals() {
        List<Referral> all = referralRepository.findAllByOrderByCreatedAtDesc();
        java.util.Map<UUID, List<Referral>> byReferrer = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(r -> r.getReferrer().getId(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
        java.util.Map<UUID, User> referrerById = all.stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getReferrer().getId(), Referral::getReferrer, (a, b) -> a));

        List<ReferrerRow> rows = byReferrer.entrySet().stream()
                .map(e -> {
                    List<Referral> referred = e.getValue();
                    long successful = referred.stream().filter(r -> r.getStatus() == com.swipeauctions.referral.enums.ReferralStatus.SUCCESSFUL).count();
                    BigDecimal totalRewardPaid = referred.stream()
                            .filter(r -> r.getStatus() == com.swipeauctions.referral.enums.ReferralStatus.SUCCESSFUL && r.getRewardAmount() != null)
                            .map(Referral::getRewardAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new ReferrerRow(e.getKey(), referrerById.get(e.getKey()).getEmail(), referred.size(), successful, totalRewardPaid);
                })
                .sorted(java.util.Comparator.comparingLong(ReferrerRow::totalReferrals).reversed())
                .toList();

        String topReferrerEmail = rows.isEmpty() ? null : rows.get(0).email();
        BigDecimal totalBonusPaid = rows.stream().map(ReferrerRow::totalRewardPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalSuccessful = rows.stream().mapToLong(ReferrerRow::successfulReferrals).sum();
        return new ReferralDashboardResponse(userRepository.count(), all.size(), totalSuccessful, totalBonusPaid, topReferrerEmail, rows);
    }

    public record ReferrerRow(UUID userId, String email, long totalReferrals, long successfulReferrals, BigDecimal totalRewardPaid) {}

    public record ReferralDashboardResponse(long totalUsers, long totalReferralsMade, long totalSuccessfulReferrals,
                                             BigDecimal totalBonusPaid, String topReferrerEmail, List<ReferrerRow> referrers) {}

    // ---- KYC ----

    @GetMapping("/kyc")
    public PageResponse<AdminKycResponse> kycQueue(@RequestParam(required = false) KycStatus status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(kycService.listForAdmin(status, pageable(page, size, "createdAt")), AdminController::toKyc);
    }

    @GetMapping("/kyc/{userId}")
    public AdminKycResponse kycRecord(@PathVariable UUID userId) {
        return toKyc(kycService.getForAdmin(userId));
    }

    @PostMapping("/kyc/{userId}/approve")
    public AdminKycResponse approveKyc(@PathVariable UUID userId,
                                        @RequestBody(required = false) KycReviewRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        String remarks = req != null ? req.remarks() : null;
        KycVerification k = kycService.approve(userId, remarks, admin.getEmail());
        auditLogService.record(admin, AuditAction.KYC_APPROVED, "Kyc", userId.toString(),
                "Approved KYC for " + k.getUser().getEmail());
        return toKyc(k);
    }

    @PostMapping("/kyc/{userId}/reject")
    public AdminKycResponse rejectKyc(@PathVariable UUID userId,
                                       @Valid @RequestBody KycReviewRequest req) {
        Admin admin = loggedInUserUtil.getCurrentAdmin();
        KycVerification k = kycService.reject(userId, req.remarks(), admin.getEmail());
        auditLogService.record(admin, AuditAction.KYC_REJECTED, "Kyc", userId.toString(),
                "Rejected KYC for " + k.getUser().getEmail() + " — " + req.remarks());
        return toKyc(k);
    }

    // ---- Stats ----

    @GetMapping("/stats")
    public StatsResponse stats() {
        long totalUsers = userRepository.count();
        long openAuctions = auctionRepository.findByStatus(AuctionStatus.OPEN).size();
        long openDisputes = disputeRepository.findByStatus(DisputeStatus.OPEN).size()
                + disputeRepository.findByStatus(DisputeStatus.IN_REVIEW).size();
        BigDecimal gmv = walletTransactionRepository.findByType(WalletTxnType.CAPTURE).stream()
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StatsResponse(totalUsers, openAuctions, gmv, openDisputes);
    }

    // ---- Analytics ----

    /** Time-bucketed counts/sums for the Overview dashboard's graphs — last {@link #BUCKET_COUNT}
     *  buckets of whichever granularity is requested. Deliberately in-memory scan + bucket, same
     *  style as {@link #stats()} above (no native SQL / date-truncation query anywhere in this
     *  codebase yet) — perfectly adequate at this app's data volume. */
    @GetMapping("/analytics")
    public AnalyticsResponse analytics(@RequestParam(defaultValue = "MONTHLY") Granularity granularity) {
        int currentIndex = bucketIndex(LocalDateTime.now(), granularity);
        List<Point> newUsers = bucketCount(
                userRepository.findAll().stream().map(User::getCreatedAt).toList(), granularity, currentIndex);
        List<Point> stockListed = bucketCount(
                listingRepository.findAll().stream().map(Listing::getCreatedAt).toList(), granularity, currentIndex);
        List<Point> stockSold = bucketCount(
                auctionRepository.findByStatus(AuctionStatus.CLOSED).stream().map(Auction::getCurrentEndTime).toList(),
                granularity, currentIndex);
        List<Point> gmv = bucketSum(walletTransactionRepository.findByType(WalletTxnType.CAPTURE), granularity, currentIndex);
        return new AnalyticsResponse(newUsers, stockListed, stockSold, gmv);
    }

    private static final int BUCKET_COUNT = 12;
    /** DAILY uses its own window — "last 30 days" is the natural unit for a daily chart, unlike the
     *  other granularities' 12-bucket window. */
    private static final int DAILY_BUCKET_COUNT = 30;

    public enum Granularity { DAILY, MONTHLY, QUARTERLY, YEARLY }

    private static int bucketCountFor(Granularity g) {
        return g == Granularity.DAILY ? DAILY_BUCKET_COUNT : BUCKET_COUNT;
    }

    private static int bucketIndex(LocalDateTime dt, Granularity g) {
        return switch (g) {
            case DAILY -> (int) dt.toLocalDate().toEpochDay();
            case MONTHLY -> dt.getYear() * 12 + (dt.getMonthValue() - 1);
            case QUARTERLY -> dt.getYear() * 4 + (dt.getMonthValue() - 1) / 3;
            case YEARLY -> dt.getYear();
        };
    }

    private static String bucketLabel(int index, Granularity g) {
        return switch (g) {
            case DAILY -> {
                java.time.LocalDate d = java.time.LocalDate.ofEpochDay(index);
                yield d.getDayOfMonth() + " " + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
            }
            case MONTHLY -> {
                int year = Math.floorDiv(index, 12);
                int month = Math.floorMod(index, 12) + 1;
                yield java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + year;
            }
            case QUARTERLY -> "Q" + (Math.floorMod(index, 4) + 1) + " " + Math.floorDiv(index, 4);
            case YEARLY -> String.valueOf(index);
        };
    }

    private static List<Point> bucketCount(List<LocalDateTime> timestamps, Granularity g, int currentIndex) {
        int bucketCount = bucketCountFor(g);
        java.util.Map<Integer, Long> counts = new java.util.HashMap<>();
        for (LocalDateTime dt : timestamps) {
            int idx = bucketIndex(dt, g);
            if (idx > currentIndex || idx <= currentIndex - bucketCount) continue;
            counts.merge(idx, 1L, Long::sum);
        }
        List<Point> points = new java.util.ArrayList<>();
        for (int i = currentIndex - bucketCount + 1; i <= currentIndex; i++) {
            points.add(new Point(bucketLabel(i, g), BigDecimal.valueOf(counts.getOrDefault(i, 0L))));
        }
        return points;
    }

    private static List<Point> bucketSum(List<com.swipeauctions.wallet.entity.WalletTransaction> txns, Granularity g, int currentIndex) {
        int bucketCount = bucketCountFor(g);
        java.util.Map<Integer, BigDecimal> sums = new java.util.HashMap<>();
        for (var t : txns) {
            int idx = bucketIndex(t.getCreatedAt(), g);
            if (idx > currentIndex || idx <= currentIndex - bucketCount) continue;
            sums.merge(idx, t.getAmount(), BigDecimal::add);
        }
        List<Point> points = new java.util.ArrayList<>();
        for (int i = currentIndex - bucketCount + 1; i <= currentIndex; i++) {
            points.add(new Point(bucketLabel(i, g), sums.getOrDefault(i, BigDecimal.ZERO)));
        }
        return points;
    }

    public record Point(String label, BigDecimal value) {}

    public record AnalyticsResponse(List<Point> newUsers, List<Point> stockListed, List<Point> stockSold, List<Point> gmv) {}

    // ---- paging / mapping helpers ----

    private static Pageable pageable(int page, int size, String sortProperty) {
        return PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(Sort.Direction.DESC, sortProperty));
    }

    UserResponse toUser(User u) {
        Wallet w = walletService.getWallet(u);
        long activeBidCount = bidRepository.countDistinctOpenAuctionsByBidder(u.getId());
        return new UserResponse(u.getId(), u.getEmail(), u.getMobileNumber(), u.getRole(),
                u.getActive(), u.getKycStatus(), u.getEmailVerified(), u.getMobileVerified(), u.getCreatedAt(),
                w.getAvailableBalance(), w.getHeldBalance(), walletService.creditLimitFor(w.getAvailableBalance()),
                u.getSubscriptionTier(), u.getSubscriptionExpiresAt(), activeBidCount);
    }

    static UserBidResponse toUserBid(Bid b) {
        Auction a = b.getAuction();
        boolean leading = a.getCurrentWinner() != null && a.getCurrentWinner().getId().equals(b.getBidder().getId());
        return new UserBidResponse(a.getId(), a.getListing().getTitle(), a.getListing().getCategory().getName(),
                b.getAmount(), a.getCurrentHighestBid(), a.getStatus(), leading, b.getPlacedAt(), a.getCurrentEndTime());
    }

    static HoldResponse toHold(BidEligibilityHold h) {
        return new HoldResponse(h.getId(), h.getAuction().getId(), h.getAuction().getListing().getTitle(),
                h.getAmount(), h.getCreatedAt());
    }

    static AdminHoldResponse toAdminHold(BidEligibilityHold h) {
        return new AdminHoldResponse(h.getId(), h.getBidder().getId(), h.getBidder().getEmail(),
                h.getAuction().getId(), h.getAuction().getListing().getTitle(), h.getAmount(), h.getCreatedAt());
    }

    static PaymentOrderResponse toPaymentOrder(PaymentOrder o) {
        return new PaymentOrderResponse(o.getId(), o.getUser().getEmail(), o.getAmount(), o.getPurpose(),
                o.getStatus(), o.getCreatedAt(), o.getCompletedAt());
    }

    static WithdrawalResponse toWithdrawal(WalletWithdrawal w) {
        return new WithdrawalResponse(w.getId(), w.getWallet().getUser().getEmail(), w.getAmount(),
                w.getStatus(), w.getRazorpayPayoutId(), w.getCreatedAt(), w.getCompletedAt());
    }

    static AdminTransactionResponse toAdminTransaction(WalletTransaction t) {
        return new AdminTransactionResponse(t.getId(), t.getWallet().getUser().getEmail(), t.getType(),
                t.getAmount(), t.getReferenceType(), t.getReferenceId(), t.getCreatedAt());
    }

    static ListingResponse toListing(Listing l) {
        return new ListingResponse(l.getId(), l.getTitle(), l.getSeller().getEmail(), l.getCategory().getId(),
                l.getCategory().getName(), l.getStatus(), l.getReservePrice(), l.getCreatedAt(), l.getRequiredTier());
    }

    static CategoryResponse toCategory(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(),
                c.getParent() != null ? c.getParent().getId() : null);
    }

    static CategoryAttributeResponse toCategoryAttribute(CategoryAttributeDef d) {
        return new CategoryAttributeResponse(d.getId(), d.getKey(), d.getLabel(), d.getValueType(),
                d.isFilterable(), d.getSortOrder());
    }

    static AdminKycResponse toKyc(KycVerification k) {
        return new AdminKycResponse(k.getUser().getId(), k.getUser().getEmail(), k.getFullName(),
                k.getDateOfBirth(), k.getAddress(), k.getCity(), k.getState(), k.getPincode(),
                k.getAadhaarMasked(), k.getPanNumberMasked(), k.getStatus(), k.getProvider(),
                k.getSubmittedAt(), k.getVerifiedAt(), k.getRemarks(), k.getReviewedBy());
    }

    AuctionResponse toAuction(Auction a) {
        User winner = a.getCurrentWinner();
        return new AuctionResponse(a.getId(), a.getListing().getId(), a.getListing().getTitle(),
                a.getListing().getSeller().getEmail(), a.getBasePrice(), a.getCurrentHighestBid(),
                a.getStatus(), a.getStartTime(), a.getCurrentEndTime(),
                bidRepository.countByAuction_Id(a.getId()),
                winner != null ? winner.getId() : null, winner != null ? winner.getEmail() : null,
                a.isSettlementPaid(), a.isWinApproved());
    }

    public record UserResponse(UUID id, String email, String mobileNumber, Role role, Boolean active,
                               KycStatus kycStatus, Boolean emailVerified, Boolean mobileVerified,
                               LocalDateTime createdAt, BigDecimal walletAvailableBalance,
                               BigDecimal walletHeldBalance, BigDecimal walletCreditLimit,
                               SubscriptionTier subscriptionTier, LocalDateTime subscriptionExpiresAt,
                               long activeBidCount) {}

    public record HoldResponse(UUID id, UUID auctionId, String listingTitle, BigDecimal amount, LocalDateTime createdAt) {}

    /** Same shape as {@link HoldResponse} plus who the hold belongs to — the admin-wide overview
     *  needs the bidder identity that the per-user endpoint doesn't (it's already scoped to one). */
    public record AdminHoldResponse(UUID id, UUID bidderId, String bidderEmail, UUID auctionId,
                                    String listingTitle, BigDecimal amount, LocalDateTime createdAt) {}

    public record PaymentOrderResponse(UUID id, String userEmail, BigDecimal amount,
                                       com.swipeauctions.wallet.enums.PaymentPurpose purpose,
                                       TopUpStatus status, LocalDateTime createdAt, LocalDateTime completedAt) {}

    public record SettlementStatusResponse(boolean paymentsEnabled, boolean payoutsEnabled) {}

    public record WithdrawalResponse(UUID id, String userEmail, BigDecimal amount, WithdrawalStatus status,
                                     String razorpayPayoutId, LocalDateTime createdAt, LocalDateTime completedAt) {}

    public record AdminTransactionResponse(UUID id, String userEmail, WalletTxnType type, BigDecimal amount,
                                           String referenceType, String referenceId, LocalDateTime createdAt) {}

    /** One row per auction this user has bid on — their own best bid vs. the auction's real current
     *  highest, so the admin can tell "accepted but trailing" from "actually winning" at a glance. */
    public record UserBidResponse(UUID auctionId, String listingTitle, String categoryName,
                                  BigDecimal yourBid, BigDecimal currentHighestBid, AuctionStatus auctionStatus,
                                  boolean leading, LocalDateTime placedAt, LocalDateTime currentEndTime) {}

    public record ReleaseHoldResponse(BigDecimal availableBalance, BigDecimal heldBalance, BigDecimal creditLimit) {}

    public record ListingResponse(UUID id, String title, String sellerEmail, UUID categoryId, String categoryName,
                                  ListingStatus status, BigDecimal reservePrice, LocalDateTime createdAt,
                                  SubscriptionTier requiredTier) {}

    public record UpdateRequiredTierRequest(@jakarta.validation.constraints.NotNull SubscriptionTier requiredTier) {}

    public record UpdateListingCategoryRequest(@jakarta.validation.constraints.NotNull UUID categoryId) {}

    public record AuctionResponse(UUID id, UUID listingId, String title, String sellerEmail,
                                  BigDecimal basePrice, BigDecimal currentHighestBid, AuctionStatus status,
                                  LocalDateTime startTime, LocalDateTime currentEndTime, long bidCount,
                                  UUID currentWinnerId, String currentWinnerEmail,
                                  boolean settlementPaid, boolean winApproved) {}

    /** One row per bidder on an auction — their own current-best bid (and that specific Bid row's
     *  id, so an admin action can target it directly), how many bids they've placed, whether
     *  they're presently leading, and whether an admin has disqualified them from this auction. */
    public record AuctionBidderResponse(UUID bidderId, String email, UUID bidId, BigDecimal amount, long bidCount,
                                        LocalDateTime lastBidAt, boolean leading, boolean disqualified) {}

    public record UpdateAuctionRequest(@NotBlank String title,
                                       @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive BigDecimal basePrice,
                                       @jakarta.validation.constraints.NotNull LocalDateTime startTime,
                                       @jakarta.validation.constraints.NotNull LocalDateTime endTime) {}

    /** refundBuyer: true = reverse escrowed sale proceeds back to the buyer; false/null = release to the seller. */
    public record ResolveDisputeRequest(String adminNotes, Boolean refundBuyer) {}

    public record StatsResponse(long totalUsers, long openAuctions, BigDecimal gmv, long openDisputes) {}

    public record CategoryResponse(UUID id, String name, String slug, UUID parentId) {}

    public record CreateCategoryRequest(@jakarta.validation.constraints.NotBlank String name,
                                        @jakarta.validation.constraints.NotBlank String slug, UUID parentId) {}

    public record CategoryAttributeResponse(UUID id, String key, String label, AttributeValueType valueType,
                                            boolean filterable, int sortOrder) {}

    public record CreateCategoryAttributeRequest(@jakarta.validation.constraints.NotBlank String key,
                                                  @jakarta.validation.constraints.NotBlank String label,
                                                  @jakarta.validation.constraints.NotNull AttributeValueType valueType,
                                                  Boolean filterable, Integer sortOrder) {}

    public record AdminKycResponse(UUID userId, String email, String fullName, LocalDate dateOfBirth,
                                   String address, String city, String state, String pincode,
                                   String aadhaarMasked, String panNumberMasked, KycStatus status,
                                   String provider, LocalDateTime submittedAt, LocalDateTime verifiedAt,
                                   String remarks, String reviewedBy) {}

    /** remarks is required when rejecting; optional when approving. */
    public record KycReviewRequest(@NotBlank(message = "Remarks are required") String remarks) {}
}
