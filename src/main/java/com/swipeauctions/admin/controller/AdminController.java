package com.swipeauctions.admin.controller;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.enums.AuditAction;
import com.swipeauctions.admin.service.AdminAuditLogService;
import com.swipeauctions.admin.service.AdminUserService;
import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.bidding.entity.Bid;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.CategoryAttributeDef;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.AttributeValueType;
import com.swipeauctions.catalog.enums.ListingStatus;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.catalog.service.CatalogService;
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
import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.enums.HoldStatus;
import com.swipeauctions.wallet.enums.WalletTxnType;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import com.swipeauctions.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

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
    private final DisputeService disputeService;
    private final DisputeRepository disputeRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CatalogService catalogService;
    private final KycService kycService;
    private final WalletService walletService;
    private final BidEligibilityHoldRepository holdRepository;
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
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = pageable(page, size, "startTime");
        var result = status != null ? auctionRepository.findByStatus(status, pageable) : auctionRepository.findAll(pageable);
        return PageResponse.of(result, this::toAuction);
    }

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

    public enum Granularity { MONTHLY, QUARTERLY, YEARLY }

    private static int bucketIndex(LocalDateTime dt, Granularity g) {
        return switch (g) {
            case MONTHLY -> dt.getYear() * 12 + (dt.getMonthValue() - 1);
            case QUARTERLY -> dt.getYear() * 4 + (dt.getMonthValue() - 1) / 3;
            case YEARLY -> dt.getYear();
        };
    }

    private static String bucketLabel(int index, Granularity g) {
        return switch (g) {
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
        java.util.Map<Integer, Long> counts = new java.util.HashMap<>();
        for (LocalDateTime dt : timestamps) {
            int idx = bucketIndex(dt, g);
            if (idx > currentIndex || idx <= currentIndex - BUCKET_COUNT) continue;
            counts.merge(idx, 1L, Long::sum);
        }
        List<Point> points = new java.util.ArrayList<>();
        for (int i = currentIndex - BUCKET_COUNT + 1; i <= currentIndex; i++) {
            points.add(new Point(bucketLabel(i, g), BigDecimal.valueOf(counts.getOrDefault(i, 0L))));
        }
        return points;
    }

    private static List<Point> bucketSum(List<com.swipeauctions.wallet.entity.WalletTransaction> txns, Granularity g, int currentIndex) {
        java.util.Map<Integer, BigDecimal> sums = new java.util.HashMap<>();
        for (var t : txns) {
            int idx = bucketIndex(t.getCreatedAt(), g);
            if (idx > currentIndex || idx <= currentIndex - BUCKET_COUNT) continue;
            sums.merge(idx, t.getAmount(), BigDecimal::add);
        }
        List<Point> points = new java.util.ArrayList<>();
        for (int i = currentIndex - BUCKET_COUNT + 1; i <= currentIndex; i++) {
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
                winner != null ? winner.getId() : null, winner != null ? winner.getEmail() : null);
    }

    public record UserResponse(UUID id, String email, String mobileNumber, Role role, Boolean active,
                               KycStatus kycStatus, Boolean emailVerified, Boolean mobileVerified,
                               LocalDateTime createdAt, BigDecimal walletAvailableBalance,
                               BigDecimal walletHeldBalance, BigDecimal walletCreditLimit,
                               SubscriptionTier subscriptionTier, LocalDateTime subscriptionExpiresAt,
                               long activeBidCount) {}

    public record HoldResponse(UUID id, UUID auctionId, String listingTitle, BigDecimal amount, LocalDateTime createdAt) {}

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
                                  UUID currentWinnerId, String currentWinnerEmail) {}

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
