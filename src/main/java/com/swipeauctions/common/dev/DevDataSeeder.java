package com.swipeauctions.common.dev;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.CategoryAttributeDef;
import com.swipeauctions.catalog.enums.AttributeValueType;
import com.swipeauctions.event.entity.AuctionEvent;
import com.swipeauctions.event.repository.AuctionEventRepository;
import com.swipeauctions.session.repository.AdminSessionRepository;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.entity.ListingAttribute;
import com.swipeauctions.catalog.entity.ListingImage;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.catalog.repository.CategoryRepository;
import com.swipeauctions.catalog.repository.ListingAttributeRepository;
import com.swipeauctions.catalog.repository.ListingImageRepository;
import com.swipeauctions.catalog.repository.ListingRepository;
import com.swipeauctions.catalog.service.CatalogService;
import com.swipeauctions.common.platform.PlatformAccountService;
import com.swipeauctions.enums.Role;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.session.entity.UserSessions;
import com.swipeauctions.session.repository.UserSessionRepository;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds demo data for local testing — only under the "dev" profile
 * ({@code --spring.profiles.active=dev}). Idempotent and additive: creates a pre-verified seller +
 * bidder (login works without OTP), several categories, and a spread of open auctions.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements CommandLineRunner {

    private static final String SELLER_EMAIL = "seller@swipeauctions.test";
    private static final String BIDDER_EMAIL = "bidder@swipeauctions.test";
    private static final String BIDDER2_EMAIL = "bidder2@swipeauctions.test";
    private static final String DEALER_EMAIL = "dealer@swipeauctions.test";
    private static final String ADMIN_EMAIL = "admin@swipeauctions.test";
    private static final String DEMO_PASSWORD = "Test@1234";

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final AdminSessionRepository adminSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;
    private final CatalogService catalogService;
    private final AuctionService auctionService;
    private final CategoryRepository categoryRepository;
    private final ListingRepository listingRepository;
    private final ListingAttributeRepository listingAttributeRepository;
    private final ListingImageRepository listingImageRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuctionEventRepository auctionEventRepository;
    private final PlatformAccountService platformAccountService;

    @Override
    public void run(String... args) {
        User seller = getOrCreateUser(SELLER_EMAIL, "9000000001", "DEV-SELLER-0001", Role.USER);
        User bidder = getOrCreateUser(BIDDER_EMAIL, "9000000002", "DEV-BIDDER-0001", Role.USER);
        // Second pre-verified bidder so multi-bidder scenarios (e.g. outbid notifications) can be tested.
        User bidder2 = getOrCreateUser(BIDDER2_EMAIL, "9000000003", "DEV-BIDDER-0002", Role.USER);
        // Dealer: pre-vetted, skips the wallet-deposit bidding gate (BidService/AuctionController).
        User dealer = getOrCreateUser(DEALER_EMAIL, "9000000004", "DEV-DEALER-0001", Role.DEALER);
        getOrCreateAdmin();

        // Dev convenience: clear stale sessions for the demo accounts each boot so repeated test logins
        // never trip the per-user device limit. Real users are unaffected (dev profile only).
        clearSessions(seller, bidder, bidder2, dealer);

        Category electronics = getOrCreateCategory("Electronics", "electronics");
        // Renamed from "Bank Assets" — getOrCreateCategory is idempotent, and Flyway V10 already
        // renamed any pre-existing "bank-assets" row's name+slug, so this resolves to the same category.
        Category bankVehicles = getOrCreateCategory("Bank Vehicles", "bank-vehicles");
        Category insurance = getOrCreateCategory("Insurance", "insurance");
        // Two more event-grouped categories (Flyway V11 also creates these for non-dev DBs) —
        // matches the CarTrade Exchange-style Events pill bar: Banks/OEM, Insurance, Premium, Auto.
        Category premium = getOrCreateCategory("Premium", "premium");
        Category auto = getOrCreateCategory("Auto", "auto");

        // Expanded seed runs once — keyed on the "vehicles" category being absent.
        if (categoryRepository.findBySlug("vehicles").isEmpty()) {
            walletService.topUp(bidder, new BigDecimal("5000000.00"));

            Category vehicles = getOrCreateCategory("Vehicles", "vehicles");
            Category properties = getOrCreateCategory("Properties", "properties");

            LocalDateTime now = LocalDateTime.now();
            seedAuction(seller, electronics, "iPhone 15 Pro (Sealed)", "Apple", ItemCondition.NEW,
                    "Bengaluru", "KA", "90000", now.minusMinutes(1), now.plusHours(6));
            seedAuction(seller, vehicles, "2021 Toyota Fortuner (Repo)", "Toyota", ItemCondition.USED,
                    "Mumbai", "MH", "1500000", now.minusMinutes(1), now.plusDays(2));
            seedAuction(seller, vehicles, "Royal Enfield Classic 350", "Royal Enfield", ItemCondition.USED,
                    "Delhi", "DL", "120000", now.minusMinutes(1), now.plusMinutes(20));
            seedAuction(seller, properties, "2BHK Apartment — Pune (Bank Auction)", null, ItemCondition.USED,
                    "Pune", "MH", "4500000", now.minusMinutes(1), now.plusDays(3));
            seedAuction(seller, bankVehicles, "Commercial Plot — Hyderabad", null, ItemCondition.USED,
                    "Hyderabad", "TG", "8000000", now.minusMinutes(1), now.plusDays(4));

            log.info("[dev-seed] expanded demo data created (categories + {} auctions)", 5);
        }

        // Idempotent every-startup passes: add a demo laptop + backfill category-specific attributes,
        // so both fresh and existing dev databases exercise the category filters.
        LocalDateTime now = LocalDateTime.now();
        ensureAuction(seller, electronics, "Dell XPS 15 (2023, Laptop)", "Dell", ItemCondition.REFURBISHED,
                "Chennai", "TN", "95000", now.minusMinutes(1), now.plusHours(8));
        seedDemoAttributes();
        Category vehicles = getOrCreateCategory("Vehicles", "vehicles");
        Category properties = getOrCreateCategory("Properties", "properties");
        seedCategoryFilters(electronics, vehicles, properties, bankVehicles, insurance, premium, auto);
        seedEventCategoryDemoData(seller, bankVehicles, insurance, premium, auto);
        seedSwipeStockDemoData();
        seedTieredLiveDemoData(seller, electronics, vehicles, properties, premium);
        seedCoverImages();

        log.info("[dev-seed] login: {} / {} (also {}, {})", BIDDER_EMAIL, DEMO_PASSWORD, BIDDER2_EMAIL, SELLER_EMAIL);
    }

    /** Deactivate any active sessions for the given demo users (dev-only login-limit reset). */
    private void clearSessions(User... users) {
        int cleared = 0;
        for (User u : users) {
            List<UserSessions> active = userSessionRepository.findByUserAndActiveTrue(u);
            for (UserSessions s : active) {
                s.setActive(false);
                s.setLogoutTime(LocalDateTime.now());
            }
            userSessionRepository.saveAll(active);
            cleared += active.size();
        }
        if (cleared > 0) log.info("[dev-seed] cleared {} stale demo session(s)", cleared);
    }

    private void seedAuction(User seller, Category category, String title, String brand, ItemCondition condition,
                             String city, String state, String price, LocalDateTime start, LocalDateTime end) {
        seedAuction(seller, category, title, brand, condition, city, state, price, start, end, false);
    }

    private void seedAuction(User seller, Category category, String title, String brand, ItemCondition condition,
                             String city, String state, String price, LocalDateTime start, LocalDateTime end,
                             boolean swipeStock) {
        Listing listing = catalogService.createListing(seller, category.getId(), title,
                title + " — seeded demo listing.", brand, condition, city, state, null, new BigDecimal(price),
                null, swipeStock);
        auctionService.createAuction(seller, listing.getId(), new BigDecimal(price), start, end, null);
    }

    /** Create an auction by title only if no listing with that title exists yet (idempotent). */
    private void ensureAuction(User seller, Category category, String title, String brand, ItemCondition condition,
                               String city, String state, String price, LocalDateTime start, LocalDateTime end) {
        ensureAuction(seller, category, title, brand, condition, city, state, price, start, end, false);
    }

    private void ensureAuction(User seller, Category category, String title, String brand, ItemCondition condition,
                               String city, String state, String price, LocalDateTime start, LocalDateTime end,
                               boolean swipeStock) {
        boolean exists = listingRepository.findAll().stream().anyMatch(l -> l.getTitle().equals(title));
        if (!exists) {
            seedAuction(seller, category, title, brand, condition, city, state, price, start, end, swipeStock);
        } else if (swipeStock) {
            // Backfill: a listing seeded before the swipeStock flag existed won't have it set.
            listingRepository.findAll().stream().filter(l -> l.getTitle().equals(title)).findFirst()
                    .filter(l -> !l.isSwipeStock())
                    .ifPresent(l -> { l.setSwipeStock(true); listingRepository.save(l); });
        }
    }

    /**
     * Seeds a couple of auction events per event-grouped category (Bank Vehicles, Insurance,
     * Premium, Auto), each with a few items — so the home page's "Bank Vehicles" / "Insurance"
     * chips (and the Premium/Auto pills inside the Events browse UI) have real events-then-items
     * data to click through in every dev environment, not just whatever a prior manual
     * curl/browser test session happened to leave behind. Idempotent by event/listing title.
     *
     * Every item also carries a "Vehicle Type" attribute (4W/CV/2W/TR/FE/3W/CE) so the Events
     * browse UI's Vehicle Type checkbox filter has real data to filter, mirroring CarTrade
     * Exchange's Events-Live dashboard.
     */
    private void seedEventCategoryDemoData(User seller, Category bankVehicles, Category insurance,
                                           Category premium, Category auto) {
        LocalDateTime now = LocalDateTime.now();

        AuctionEvent bvLive = ensureEvent(seller, bankVehicles, "SBI Bank Vehicles — South Zone",
                "Chennai", now.minusHours(2), now.plusDays(3));
        ensureEventAuction(seller, bvLive, bankVehicles, "Repossessed Maruti Suzuki Swift VXI", "Maruti Suzuki",
                ItemCondition.USED, "Chennai", "TN", "350000", now.minusMinutes(1), now.plusDays(2),
                Map.of("Year", "2019", "Fuel", "Petrol", "Reg No", "TN07AB1234", "KM driven", "62000", "Vehicle Type", "4W"));
        ensureEventAuction(seller, bvLive, bankVehicles, "Repossessed Mahindra Bolero Pickup", "Mahindra",
                ItemCondition.USED, "Chennai", "TN", "420000", now.minusMinutes(1), now.plusDays(2),
                Map.of("Year", "2020", "Fuel", "Diesel", "Reg No", "TN09CD5678", "KM driven", "48000", "Vehicle Type", "CV"));

        AuctionEvent bvUpcoming = ensureEvent(seller, bankVehicles, "HDFC Bank Vehicles — West Zone",
                "Mumbai", now.plusDays(1), now.plusDays(6));
        ensureEventAuction(seller, bvUpcoming, bankVehicles, "Repossessed Hyundai Creta SX", "Hyundai",
                ItemCondition.USED, "Mumbai", "MH", "900000", now.plusDays(1), now.plusDays(5),
                Map.of("Year", "2021", "Fuel", "Diesel", "Reg No", "MH02EF9012", "KM driven", "31000", "Vehicle Type", "4W"));

        AuctionEvent insLive = ensureEvent(seller, insurance, "ICICI Lombard Salvage — East Zone",
                "Kolkata", now.minusHours(1), now.plusDays(3));
        ensureEventAuction(seller, insLive, insurance, "Accident-Damaged Honda City", "Honda",
                ItemCondition.FOR_PARTS, "Kolkata", "WB", "180000", now.minusMinutes(1), now.plusDays(2),
                Map.of("Policy Type", "Motor", "IDV", "650000", "Claim No", "ICL-2026-88213", "Vehicle Type", "4W"));
        ensureEventAuction(seller, insLive, insurance, "Fire-Damaged Retail Stock Lot", null,
                ItemCondition.FOR_PARTS, "Kolkata", "WB", "95000", now.minusMinutes(1), now.plusDays(2),
                Map.of("Policy Type", "Property", "Sum Insured", "1200000", "Claim No", "ICL-2026-88214", "Vehicle Type", "CE"));

        AuctionEvent insUpcoming = ensureEvent(seller, insurance, "Bajaj Allianz Salvage — North Zone",
                "Delhi", now.plusDays(1), now.plusDays(6));
        ensureEventAuction(seller, insUpcoming, insurance, "Flood-Damaged Royal Enfield Meteor", "Royal Enfield",
                ItemCondition.FOR_PARTS, "Delhi", "DL", "75000", now.plusDays(1), now.plusDays(5),
                Map.of("Policy Type", "Motor", "IDV", "220000", "Claim No", "BAGIC-2026-44120", "Vehicle Type", "2W"));

        AuctionEvent premLive = ensureEvent(seller, premium, "Premium Fleet Auction — Bengaluru",
                "Bengaluru", now.minusHours(1), now.plusDays(3));
        ensureEventAuction(seller, premLive, premium, "2022 Mercedes-Benz C-Class (Fleet Return)", "Mercedes-Benz",
                ItemCondition.USED, "Bengaluru", "KA", "3200000", now.minusMinutes(1), now.plusDays(2),
                Map.of("Year", "2022", "Fuel", "Petrol", "KM driven", "18000", "Vehicle Type", "4W"));

        AuctionEvent premUpcoming = ensureEvent(seller, premium, "Premium Fleet Auction — Gurugram",
                "Gurugram", now.plusDays(1), now.plusDays(6));
        ensureEventAuction(seller, premUpcoming, premium, "2023 BMW 3 Series (Lease Return)", "BMW",
                ItemCondition.USED, "Gurugram", "HR", "3800000", now.plusDays(1), now.plusDays(5),
                Map.of("Year", "2023", "Fuel", "Diesel", "KM driven", "9000", "Vehicle Type", "4W"));

        AuctionEvent autoLive = ensureEvent(seller, auto, "OEM Auto Auction — Pune",
                "Pune", now.minusHours(1), now.plusDays(3));
        ensureEventAuction(seller, autoLive, auto, "Bajaj RE Auto (Fleet Surplus)", "Bajaj",
                ItemCondition.USED, "Pune", "MH", "210000", now.minusMinutes(1), now.plusDays(2),
                Map.of("Year", "2020", "Fuel", "CNG", "KM driven", "55000", "Vehicle Type", "3W"));
        ensureEventAuction(seller, autoLive, auto, "TVS King Duramax (Fleet Surplus)", "TVS",
                ItemCondition.USED, "Pune", "MH", "195000", now.minusMinutes(1), now.plusDays(2),
                Map.of("Year", "2021", "Fuel", "Diesel", "KM driven", "42000", "Vehicle Type", "TR/FE"));
    }

    /**
     * More live auctions gated behind the GOLD/DIAMOND subscription tiers, so the paywall UI
     * (locked card, tier badge, "Upgrade your plan" CTA) has real live data to exercise across
     * devices, not just whatever a single earlier test listing happened to be. Idempotent by title.
     */
    private void seedTieredLiveDemoData(User seller, Category electronics, Category vehicles,
                                        Category properties, Category premium) {
        LocalDateTime now = LocalDateTime.now();

        // ---- GOLD-gated ----
        ensureAuctionWithTier(seller, electronics, "Apple MacBook Pro 16\" M3 Max (Sealed)", "Apple", ItemCondition.NEW,
                "Mumbai", "MH", "320000", now.minusMinutes(1), now.plusHours(6), SubscriptionTier.GOLD);
        addAttributes("Apple MacBook Pro 16\" M3 Max (Sealed)", Map.of("RAM", "36 GB", "Storage", "1 TB", "Screen size", "16.2\""));

        ensureAuctionWithTier(seller, vehicles, "2022 Jeep Compass (Repo)", "Jeep", ItemCondition.USED,
                "Pune", "MH", "1800000", now.minusMinutes(1), now.plusDays(1), SubscriptionTier.GOLD);
        addAttributes("2022 Jeep Compass (Repo)", Map.of(
                "Year", "2022", "Fuel", "Diesel", "Transmission", "Automatic", "KM driven", "24000"));

        ensureAuctionWithTier(seller, premium, "2023 Volvo XC60 (Fleet Return)", "Volvo", ItemCondition.USED,
                "Bengaluru", "KA", "4200000", now.minusMinutes(1), now.plusDays(2), SubscriptionTier.GOLD);
        addAttributes("2023 Volvo XC60 (Fleet Return)", Map.of("Year", "2023", "Fuel", "Petrol", "KM driven", "6000"));

        // ---- DIAMOND-gated ----
        ensureAuctionWithTier(seller, properties, "4BHK Luxury Penthouse — Mumbai (Bank Auction)", null, ItemCondition.USED,
                "Mumbai", "MH", "25000000", now.minusMinutes(1), now.plusDays(3), SubscriptionTier.DIAMOND);
        addAttributes("4BHK Luxury Penthouse — Mumbai (Bank Auction)", Map.of(
                "Bedrooms", "4 BHK", "Furnishing", "Furnished", "Area (sqft)", "3800"));

        ensureAuctionWithTier(seller, vehicles, "2023 Range Rover Sport (Repo)", "Land Rover", ItemCondition.USED,
                "Delhi", "DL", "9500000", now.minusMinutes(1), now.plusDays(1), SubscriptionTier.DIAMOND);
        addAttributes("2023 Range Rover Sport (Repo)", Map.of(
                "Year", "2023", "Fuel", "Diesel", "Transmission", "Automatic", "KM driven", "4000"));

        ensureAuctionWithTier(seller, premium, "2023 Porsche Cayenne (Fleet Return)", "Porsche", ItemCondition.USED,
                "Gurugram", "HR", "12000000", now.minusMinutes(1), now.plusDays(2), SubscriptionTier.DIAMOND);
        addAttributes("2023 Porsche Cayenne (Fleet Return)", Map.of("Year", "2023", "Fuel", "Petrol", "KM driven", "3000"));
    }

    /** Like {@link #ensureAuction}, but for a listing gated behind a subscription tier. */
    private void ensureAuctionWithTier(User seller, Category category, String title, String brand,
                                       ItemCondition condition, String city, String state, String price,
                                       LocalDateTime start, LocalDateTime end, SubscriptionTier requiredTier) {
        if (listingRepository.findAll().stream().anyMatch(l -> l.getTitle().equals(title))) return;
        Listing listing = catalogService.createListing(seller, category.getId(), title,
                title + " — seeded demo listing.", brand, condition, city, state, null, new BigDecimal(price),
                null, false, requiredTier);
        auctionService.createAuction(seller, listing.getId(), new BigDecimal(price), start, end, null);
    }

    /**
     * A couple of demo listings owned by the Swipe Stock platform account, with the {@code swipeStock}
     * flag set, so the /swipe-stock page — which filters the browse grid to that flag, same UI as
     * everywhere else — has real data to show. Deliberately spans two different categories
     * (Electronics, Vehicles) rather than the "Swipe Stock" category, to prove the page filters by
     * the flag, not by category (any category's items can be flagged as Swipe Stock).
     */
    private void seedSwipeStockDemoData() {
        User swipeStockSeller = platformAccountService.getOrCreateSwipeStockSeller();
        platformAccountService.getOrCreateSwipeStockCategory();
        LocalDateTime now = LocalDateTime.now();

        Category electronics = categoryRepository.findBySlug("electronics").orElseThrow();
        ensureAuction(swipeStockSeller, electronics, "Swipe Stock — Refurbished MacBook Air M2", "Apple",
                ItemCondition.REFURBISHED, "Bengaluru", "KA", "78000", now.minusMinutes(1), now.plusDays(3), true);
        addAttributes("Swipe Stock — Refurbished MacBook Air M2", Map.of(
                "RAM", "8 GB", "Storage", "256 GB", "Screen size", "13.6\""));

        Category vehicles = categoryRepository.findBySlug("vehicles").orElseThrow();
        ensureAuction(swipeStockSeller, vehicles, "Swipe Stock — Certified Pre-Owned Royal Enfield Hunter 350",
                "Royal Enfield", ItemCondition.REFURBISHED, "Pune", "MH", "165000", now.minusMinutes(1), now.plusDays(3), true);
        addAttributes("Swipe Stock — Certified Pre-Owned Royal Enfield Hunter 350", Map.of(
                "Year", "2023", "Fuel", "Petrol", "KM driven", "3200"));
    }

    /** Look up an auction event by name, creating it only if missing (idempotent across dev boots). */
    private AuctionEvent ensureEvent(User seller, Category category, String name, String location,
                                     LocalDateTime start, LocalDateTime closing) {
        return auctionEventRepository.findAll().stream()
                .filter(e -> e.getName().equals(name))
                .findFirst()
                .orElseGet(() -> auctionEventRepository.save(AuctionEvent.builder()
                        .seller(seller).category(category).name(name).location(location)
                        .startTime(start).closingTime(closing).build()));
    }

    /**
     * Create a listing + auction attached to an event, by title, only if it doesn't exist yet.
     * Attributes are backfilled every startup regardless (addAttributes only adds missing keys) so
     * a newly added attribute — e.g. "Vehicle Type" — reaches listings seeded in an earlier session.
     */
    private void ensureEventAuction(User seller, AuctionEvent event, Category category, String title, String brand,
                                    ItemCondition condition, String city, String state, String price,
                                    LocalDateTime start, LocalDateTime end, Map<String, String> attrs) {
        if (listingRepository.findAll().stream().noneMatch(l -> l.getTitle().equals(title))) {
            Listing listing = catalogService.createListing(seller, category.getId(), title,
                    title + " — seeded demo listing.", brand, condition, city, state, null, new BigDecimal(price), null);
            auctionService.createAuction(seller, listing.getId(), new BigDecimal(price), start, end, event.getId());
        }
        addAttributes(title, attrs);
    }

    /** Web-informed category specs for the demo listings; only missing keys are added (idempotent). */
    private void seedDemoAttributes() {
        addAttributes("iPhone 15 Pro (Sealed)", Map.of(
                "RAM", "8 GB", "Storage", "256 GB", "Screen size", "6.1\""));
        addAttributes("Dell XPS 15 (2023, Laptop)", Map.of(
                "RAM", "16 GB", "Storage", "512 GB SSD", "Screen size", "15.6\"", "Processor", "Intel Core i7"));
        addAttributes("2021 Toyota Fortuner (Repo)", Map.of(
                "Year", "2021", "Fuel", "Diesel", "Transmission", "Automatic", "KM driven", "45000"));
        addAttributes("Royal Enfield Classic 350", Map.of(
                "Year", "2020", "Fuel", "Petrol", "Transmission", "Manual", "KM driven", "12000"));
        addAttributes("2BHK Apartment — Pune (Bank Auction)", Map.of(
                "Bedrooms", "2 BHK", "Furnishing", "Semi-furnished", "Area (sqft)", "1150"));
        addAttributes("Commercial Plot — Hyderabad", Map.of(
                "Zoning", "Commercial", "Area (sqft)", "4000"));
    }

    /**
     * Declares which specs each existing category exposes as a browse-page filter (the actual dropdown
     * option values are derived live from real {@code ListingAttribute} data at request time — see
     * {@code CatalogService.listPublicFilters} — so nothing about the option lists lives here). Mirrors
     * the filter set the frontend used to hardcode in {@code catalogFilters.ts}. Idempotent per category.
     */
    private void seedCategoryFilters(Category electronics, Category vehicles, Category properties,
                                      Category bankVehicles, Category insurance, Category premium, Category auto) {
        ensureFilters(electronics,
                new FilterDef("RAM", "RAM", AttributeValueType.ENUM, 1),
                new FilterDef("Storage", "Storage", AttributeValueType.ENUM, 2),
                new FilterDef("Screen size", "Screen size", AttributeValueType.ENUM, 3));
        ensureFilters(vehicles,
                new FilterDef("Fuel", "Fuel type", AttributeValueType.ENUM, 1),
                new FilterDef("Transmission", "Transmission", AttributeValueType.ENUM, 2),
                new FilterDef("Year", "Year (from)", AttributeValueType.NUMBER, 3),
                new FilterDef("KM driven", "Max KM driven", AttributeValueType.NUMBER, 4));
        ensureFilters(properties,
                new FilterDef("Bedrooms", "Bedrooms", AttributeValueType.ENUM, 1),
                new FilterDef("Furnishing", "Furnishing", AttributeValueType.ENUM, 2),
                new FilterDef("Area (sqft)", "Min area (sqft)", AttributeValueType.NUMBER, 3));
        for (Category vehicleEventCategory : List.of(bankVehicles, insurance, premium, auto)) {
            ensureFilters(vehicleEventCategory,
                    new FilterDef("Fuel", "Fuel type", AttributeValueType.ENUM, 1),
                    new FilterDef("Year", "Year (from)", AttributeValueType.NUMBER, 2),
                    new FilterDef("KM driven", "Max KM driven", AttributeValueType.NUMBER, 3));
        }
    }

    private record FilterDef(String key, String label, AttributeValueType valueType, int sortOrder) {}

    private void ensureFilters(Category category, FilterDef... defs) {
        Set<String> existing = catalogService.listCategoryAttributes(category.getId()).stream()
                .map(CategoryAttributeDef::getKey).collect(Collectors.toSet());
        for (FilterDef d : defs) {
            if (!existing.contains(d.key())) {
                catalogService.addCategoryAttribute(category.getId(), d.key(), d.label(), d.valueType(), true, d.sortOrder());
            }
        }
    }

    /**
     * Attach web-sourced photos to each demo listing — most get one cover photo, but a couple
     * (Toyota Fortuner, Royal Enfield) get several angles so the item detail page's multi-image
     * gallery (thumbnail rail + swappable main image) has real data to show, not just a single
     * photo. Idempotent by URL, so re-running after adding more angles backfills only what's
     * missing rather than skipping listings that already have one image.
     */
    private void seedCoverImages() {
        Map<String, List<String>> covers = new java.util.LinkedHashMap<>();
        covers.put("iPhone 15 Pro (Sealed)", List.of(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/Apple_iPhone_15_Pro.jpg/960px-Apple_iPhone_15_Pro.jpg"));
        covers.put("Dell XPS 15 (2023, Laptop)", List.of(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/2/21/DELL_XPS_13_and_15_%2837080596413%29.jpg/960px-DELL_XPS_13_and_15_%2837080596413%29.jpg"));
        covers.put("MacBook Pro 14\" (2023)", List.of(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/MacBook_Pro_16_%28M1_Pro%2C_2021%29_-_Wikipedia.jpg/960px-MacBook_Pro_16_%28M1_Pro%2C_2021%29_-_Wikipedia.jpg"));
        covers.put("2021 Toyota Fortuner (Repo)", List.of(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/6/66/2015_Toyota_Fortuner_%28New_Zealand%29.jpg/960px-2015_Toyota_Fortuner_%28New_Zealand%29.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/8/83/Toyota_Fortuner-rear.JPG/960px-Toyota_Fortuner-rear.JPG",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8c/2008-2010_Toyota_Fortuner%2C_first_generation%2C_rear_view.jpg/960px-2008-2010_Toyota_Fortuner%2C_first_generation%2C_rear_view.jpg"));
        covers.put("Royal Enfield Classic 350", List.of(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0f/Royal_Enfield_Classic_350_%282017_Model_Year%29.jpg/960px-Royal_Enfield_Classic_350_%282017_Model_Year%29.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/2/26/Royal_Enfield_Classic_350_2010_Model.jpg/960px-Royal_Enfield_Classic_350_2010_Model.jpg"));
        covers.put("2BHK Apartment — Pune (Bank Auction)", List.of(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Modern_living_room_with_stylish_furniture_and_a_view_of_the_outdoors_in_a_cozy_apartment_setting.jpg/960px-Modern_living_room_with_stylish_furniture_and_a_view_of_the_outdoors_in_a_cozy_apartment_setting.jpg"));
        covers.put("Commercial Plot — Hyderabad", List.of(
                "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2c/Vacant_plot%2C_Purley_Way_-_geograph.org.uk_-_2497437.jpg/960px-Vacant_plot%2C_Purley_Way_-_geograph.org.uk_-_2497437.jpg"));
        // Swipe Stock demo listings intentionally get no explicit cover here — they fall back to the
        // deterministic keyword-photo (frontend cardImage()), same as any other uncovered listing.

        // seedTieredLiveDemoData's items — no single "the" photo per title like the curated ones
        // above, so each gets a small keyword-matched gallery from loremflickr.com instead (the same
        // source the frontend's cardImage() fallback already uses for any uncovered listing).
        covers.put("Apple MacBook Pro 16\" M3 Max (Sealed)", demoGallery("Apple MacBook Pro 16\" M3 Max (Sealed)", "macbook,laptop,apple", 3));
        covers.put("2022 Jeep Compass (Repo)", demoGallery("2022 Jeep Compass (Repo)", "jeep,compass,suv,car", 3));
        covers.put("2023 Volvo XC60 (Fleet Return)", demoGallery("2023 Volvo XC60 (Fleet Return)", "volvo,xc60,suv,car", 3));
        covers.put("4BHK Luxury Penthouse — Mumbai (Bank Auction)", demoGallery("4BHK Luxury Penthouse — Mumbai (Bank Auction)", "penthouse,luxury,apartment", 3));
        covers.put("2023 Range Rover Sport (Repo)", demoGallery("2023 Range Rover Sport (Repo)", "range rover,suv,car", 3));
        covers.put("2023 Porsche Cayenne (Fleet Return)", demoGallery("2023 Porsche Cayenne (Fleet Return)", "porsche,cayenne,suv,car", 3));

        int added = 0;
        for (Listing l : listingRepository.findAll()) {
            List<String> urls = covers.get(l.getTitle());
            if (urls == null) continue;
            List<ListingImage> existing = listingImageRepository.findByListing_IdOrderBySortOrderAsc(l.getId());
            Set<String> existingUrls = existing.stream().map(ListingImage::getUrl).collect(Collectors.toSet());
            boolean hasCover = existing.stream().anyMatch(ListingImage::isCover);
            int nextOrder = existing.size();
            for (String url : urls) {
                if (existingUrls.contains(url)) continue;
                listingImageRepository.save(ListingImage.builder()
                        .listing(l).url(url).sortOrder(nextOrder).cover(!hasCover).build());
                hasCover = true;
                nextOrder++;
                added++;
            }
        }
        if (added > 0) log.info("[dev-seed] attached {} demo listing image(s)", added);
    }

    /**
     * {@code count} distinct, keyword-relevant stock photos for a demo listing that doesn't have a
     * real upload/one curated URL — pinned to deterministic loremflickr.com "lock" seeds (derived
     * from the title) so the result is stable across restarts instead of changing every run.
     */
    private List<String> demoGallery(String title, String keywords, int count) {
        List<String> urls = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            long lock = Math.abs((long) (title + "#" + i).hashCode());
            urls.add("https://loremflickr.com/800/600/" + keywords + "?lock=" + lock);
        }
        return urls;
    }

    private void addAttributes(String title, Map<String, String> attrs) {
        listingRepository.findAll().stream()
                .filter(l -> l.getTitle().equals(title))
                .findFirst()
                .ifPresent(l -> {
                    Set<String> present = listingAttributeRepository.findByListing_Id(l.getId()).stream()
                            .map(ListingAttribute::getKey).collect(Collectors.toSet());
                    attrs.forEach((k, v) -> {
                        if (!present.contains(k)) {
                            listingAttributeRepository.save(
                                    ListingAttribute.builder().listing(l).key(k).value(v).build());
                        }
                    });
                });
    }

    private Category getOrCreateCategory(String name, String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseGet(() -> catalogService.createCategory(name, slug, null));
    }

    private User getOrCreateUser(String email, String mobile, String ref, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = userRepository.save(User.builder()
                    .role(role).email(email).mobileNumber(mobile)
                    .password(passwordEncoder.encode(DEMO_PASSWORD))
                    .active(true).emailVerified(true).mobileVerified(true)
                    // Pre-verified so demo bidding isn't blocked by the KYC-before-bidding gate.
                    .kycCompleted(true).kycStatus(com.swipeauctions.enums.KycStatus.APPROVED)
                    .userRefNumber(ref).build());
            walletService.topUp(u, new BigDecimal("100000.00"));
            return u;
        });
    }

    private void getOrCreateAdmin() {
        Admin admin = adminRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> adminRepository.save(Admin.builder()
                .firstName("Swipe").lastName("Admin").email(ADMIN_EMAIL).mobileNumber("9000000099")
                .password(passwordEncoder.encode(DEMO_PASSWORD)).role(Role.ADMIN).active(true).build()));

        // Dev convenience: the admin auth stack allows only one active session at a time (no
        // configurable limit like the user side) — clear any left over from a prior test run so
        // repeated dev logins never get stuck with "Admin already logged in on another device".
        adminSessionRepository.findByAdminAndActiveTrue(admin).ifPresent(s -> {
            s.setActive(false);
            s.setLogoutTime(LocalDateTime.now());
            adminSessionRepository.save(s);
        });
    }
}
