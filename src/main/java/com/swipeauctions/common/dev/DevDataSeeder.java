package com.swipeauctions.common.dev;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.enums.AdminRole;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.CategoryAttributeDef;
import com.swipeauctions.catalog.enums.AttributeValueType;
import com.swipeauctions.dispute.repository.DisputeRepository;
import com.swipeauctions.event.entity.AuctionEvent;
import com.swipeauctions.event.repository.AuctionEventRepository;
import com.swipeauctions.session.repository.AdminSessionRepository;
import com.swipeauctions.catalog.entity.Listing;
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
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.repository.SaleProceedsHoldRepository;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds demo data for local testing — only under the "dev" profile
 * ({@code --spring.profiles.active=dev}). Users/categories/filters are idempotent and additive
 * across boots. The catalog itself (listings/auctions/events) is a one-time destructive refresh:
 * the first boot after this dataset changed wipes any prior demo catalog and recreates a full,
 * consistent spread (10 live + 10 upcoming + 10 closed per category); every boot after that is a
 * no-op for the catalog (gated on {@link #alreadySeeded()}), so manual in-app testing state
 * (extra bids, listings, etc.) survives ordinary restarts.
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

    /** Above the old dataset's ~38 rows, below the new dataset's ~212+ — used to detect a stale catalog. */
    private static final int CATALOG_ALREADY_SEEDED_THRESHOLD = 150;

    private static final String[][] CITIES = {
            {"Bengaluru", "KA"}, {"Mumbai", "MH"}, {"Delhi", "DL"}, {"Chennai", "TN"}, {"Hyderabad", "TG"},
            {"Pune", "MH"}, {"Kolkata", "WB"}, {"Ahmedabad", "GJ"}, {"Jaipur", "RJ"}, {"Lucknow", "UP"},
            {"Chandigarh", "CH"}, {"Kochi", "KL"}, {"Indore", "MP"}, {"Nagpur", "MH"}, {"Surat", "GJ"},
            {"Coimbatore", "TN"}, {"Bhopal", "MP"}, {"Patna", "BR"}, {"Vadodara", "GJ"}, {"Nashik", "MH"},
            {"Gurugram", "HR"}, {"Noida", "UP"}, {"Thane", "MH"}, {"Visakhapatnam", "AP"}, {"Ludhiana", "PB"},
            {"Agra", "UP"}, {"Kanpur", "UP"}, {"Rajkot", "GJ"}, {"Varanasi", "UP"}, {"Amritsar", "PB"},
    };

    private static final String[] MASS_MARKET_VEHICLES = {
            "Maruti Suzuki Swift", "Maruti Suzuki Baleno", "Maruti Suzuki Dzire", "Maruti Suzuki Ertiga",
            "Maruti Suzuki Brezza", "Hyundai i20", "Hyundai Creta", "Hyundai Venue", "Hyundai Verna", "Hyundai Aura",
            "Tata Nexon", "Tata Punch", "Tata Altroz", "Tata Tiago", "Tata Harrier",
            "Honda City", "Honda Amaze", "Honda WR-V", "Mahindra XUV700", "Mahindra Scorpio",
            "Kia Seltos", "Kia Sonet", "Toyota Innova Crysta", "Toyota Glanza", "Toyota Urban Cruiser",
            "Skoda Slavia", "Volkswagen Virtus", "Renault Kwid", "Renault Triber", "Nissan Magnite",
    };

    private static final String[] LUXURY_VEHICLES = {
            "Mercedes-Benz C-Class", "Mercedes-Benz E-Class", "Mercedes-Benz GLC", "Mercedes-Benz GLE", "Mercedes-Benz A-Class",
            "BMW 3 Series", "BMW 5 Series", "BMW X1", "BMW X3", "BMW X5",
            "Audi A4", "Audi A6", "Audi Q3", "Audi Q5", "Audi Q7",
            "Volvo XC40", "Volvo XC60", "Volvo XC90", "Land Rover Discovery Sport", "Land Rover Range Rover Evoque",
            "Jaguar XF", "Jaguar F-Pace", "Porsche Macan", "Porsche Cayenne", "Mini Cooper",
            "Lexus ES", "Lexus NX", "Jeep Compass", "Jeep Meridian", "Volvo S60",
    };

    private static final String[] COMMERCIAL_VEHICLES = {
            "Bajaj RE Auto", "Bajaj Maxima Cargo", "TVS King Duramax", "Piaggio Ape Xtra", "Piaggio Porter 700",
            "Mahindra Alfa Plus", "Mahindra Bolero Pickup", "Atul Gem", "Force Tempo Traveller", "Tata Ace Gold",
            "Tata 407 Truck", "Ashok Leyland Dost", "Eicher Pro 2049", "Mahindra Jeeto", "Tata Intra V30",
            "Bajaj Compact RE", "TVS Apache RTR (Fleet)", "Hero Splendor Plus (Fleet)", "Royal Enfield Hunter 350 (Fleet)", "Honda Activa (Fleet)",
            "TVS Jupiter (Fleet)", "Bajaj Pulsar (Fleet)", "Yamaha FZ (Fleet)", "Suzuki Access (Fleet)", "Mahindra Supro Cargo",
            "Tata Winger", "Force Traveller Ambulance", "Eicher Skyline Bus", "Ashok Leyland Bus", "Piaggio Ape City",
    };

    /** Vehicle Type ({@code VEHICLE_TYPE_KEY} on the frontend) for each {@link #COMMERCIAL_VEHICLES} entry,
     *  index-for-index — an auto-rickshaw isn't a "2W" and a bus isn't a "3W", so this has to be a real
     *  per-model mapping rather than a cycled guess. */
    private static final String[] COMMERCIAL_VEHICLE_TYPES = {
            "3W", "CV", "3W", "3W", "CV",
            "3W", "CV", "3W", "TR/FE", "CV",
            "CV", "CV", "CV", "CV", "CV",
            "3W", "2W", "2W", "2W", "2W",
            "2W", "2W", "2W", "2W", "CV",
            "TR/FE", "TR/FE", "TR/FE", "TR/FE", "3W",
    };

    /**
     * Bank Vehicles / Insurance: a bank or insurer's repossessed/salvage fleet is realistically mixed
     * (cars, bikes, a few commercial pickups) — never one uniform vehicle type. Bucket-grouped in
     * threes of 10 (live/upcoming/closed), each bucket 6 cars (4W) + 2 bikes (2W) + 2 commercial (CV),
     * so every bucket also has real Vehicle Type variety to split into separate, type-pure events —
     * see {@link #seedEventGroupedCategory}'s "don't mix vehicle types in one event/listing" grouping.
     */
    private static final String[] MIXED_FLEET_MODELS = {
            "Maruti Suzuki Swift", "Maruti Suzuki Baleno", "Maruti Suzuki Dzire", "Hyundai i20", "Hyundai Creta", "Hyundai Venue",
            "Royal Enfield Classic 350", "Bajaj Pulsar 150", "Tata Ace Gold", "Mahindra Bolero Pickup",
            "Tata Nexon", "Tata Punch", "Tata Altroz", "Honda City", "Honda Amaze", "Mahindra XUV700",
            "Hero Splendor Plus", "TVS Apache RTR", "Ashok Leyland Dost", "Eicher Pro 2049",
            "Kia Seltos", "Kia Sonet", "Toyota Innova Crysta", "Toyota Glanza", "Skoda Slavia", "Volkswagen Virtus",
            "Honda Activa 6G", "Yamaha FZ-S", "Force Tempo Traveller", "Piaggio Porter 700",
    };
    private static final String[] MIXED_FLEET_TYPES = {
            "4W", "4W", "4W", "4W", "4W", "4W", "2W", "2W", "CV", "CV",
            "4W", "4W", "4W", "4W", "4W", "4W", "2W", "2W", "CV", "CV",
            "4W", "4W", "4W", "4W", "4W", "4W", "2W", "2W", "CV", "CV",
    };

    /** Premium fleet is passenger cars only in this seed set — every item is "4W". */
    private static final String[] PREMIUM_TYPES = uniform30("4W");

    private static String[] uniform30(String value) {
        String[] a = new String[30];
        java.util.Arrays.fill(a, value);
        return a;
    }

    private static final String[] ELECTRONICS_ITEMS = {
            "Apple iPhone 15 Pro", "Apple iPhone 14", "Samsung Galaxy S24 Ultra", "Samsung Galaxy Z Fold5", "OnePlus 12",
            "Apple MacBook Pro 14\"", "Apple MacBook Air M2", "Dell XPS 15", "HP Spectre x360", "Lenovo ThinkPad X1",
            "Apple iPad Pro 12.9\"", "Samsung Galaxy Tab S9", "Sony PS5 Slim", "Microsoft Xbox Series X", "Nintendo Switch OLED",
            "Sony WH-1000XM5 Headphones", "Apple Watch Ultra 2", "Samsung Galaxy Watch 6", "Canon EOS R6", "Nikon Z6 II",
            "Sony Bravia 65\" OLED TV", "Samsung 55\" QLED TV", "LG 4K Projector", "Bose SoundLink Speaker", "JBL Party Box",
            "Dyson V15 Vacuum", "GoPro Hero 12", "DJI Mavic 3 Drone", "Apple AirPods Max", "Samsung Galaxy Buds2 Pro",
    };

    private static final String[] PROPERTY_ITEMS = {
            "1BHK Flat", "1BHK Flat", "2BHK Apartment", "2BHK Apartment", "3BHK Apartment",
            "3BHK Villa", "4BHK Villa", "4BHK Luxury Penthouse", "Studio Apartment", "2BHK Row House",
            "Commercial Shop", "Commercial Office Space", "Commercial Plot", "Residential Plot", "Industrial Shed",
            "Farmhouse", "2BHK Duplex", "3BHK Duplex", "Warehouse", "Retail Showroom",
            "1BHK Studio", "2BHK Independent House", "3BHK Independent House", "Agricultural Land", "Commercial Complex Unit",
            "Bungalow", "Penthouse Suite", "Service Apartment", "Godown Space", "Corner Plot",
    };

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
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final BidEligibilityHoldRepository bidEligibilityHoldRepository;
    private final SaleProceedsHoldRepository saleProceedsHoldRepository;
    private final DisputeRepository disputeRepository;
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
        Category bankVehicles = getOrCreateCategory("Bank Vehicles", "bank-vehicles");
        Category insurance = getOrCreateCategory("Insurance", "insurance");
        Category premium = getOrCreateCategory("Premium", "premium");
        Category auto = getOrCreateCategory("Auto", "auto");
        Category vehicles = getOrCreateCategory("Vehicles", "vehicles");
        Category properties = getOrCreateCategory("Properties", "properties");

        seedCategoryFilters(electronics, vehicles, properties, bankVehicles, insurance, premium, auto);

        if (!alreadySeeded()) {
            wipeDemoCatalog();
            walletService.topUp(bidder, new BigDecimal("5000000.00"));

            seedVehicles(seller, vehicles);
            seedProperties(seller, properties);
            seedElectronics(seller, electronics);
            seedEventGroupedCategory(seller, bankVehicles, MIXED_FLEET_MODELS, MIXED_FLEET_TYPES, "Bank Vehicles Auction",
                    "Repossessed", ItemCondition.USED, 250_000, 18_500, false, false);
            seedEventGroupedCategory(seller, insurance, MIXED_FLEET_MODELS, MIXED_FLEET_TYPES, "Insurance Salvage Auction",
                    "Damaged", ItemCondition.FOR_PARTS, 90_000, 9_500, true, false);
            seedEventGroupedCategory(seller, premium, LUXURY_VEHICLES, PREMIUM_TYPES, "Premium Fleet Auction",
                    "Fleet Return", ItemCondition.USED, 2_800_000, 215_000, false, true);
            seedEventGroupedCategory(seller, auto, COMMERCIAL_VEHICLES, COMMERCIAL_VEHICLE_TYPES, "Auto & Commercial Auction",
                    "Fleet Surplus", ItemCondition.USED, 120_000, 8_700, false, false);
            seedSwipeStockDemoData(electronics, vehicles);
            Map<java.util.UUID, String> categorySlugById = Map.of(
                    electronics.getId(), electronics.getSlug(), bankVehicles.getId(), bankVehicles.getSlug(),
                    insurance.getId(), insurance.getSlug(), premium.getId(), premium.getSlug(),
                    auto.getId(), auto.getSlug(), vehicles.getId(), vehicles.getSlug(),
                    properties.getId(), properties.getSlug());
            seedGenericCoverImages(categorySlugById);

            log.info("[dev-seed] full catalog refresh: {} listings across 7 categories (10 live / 10 upcoming / 10 closed each)",
                    listingRepository.count());
        }

        log.info("[dev-seed] login: {} / {} (also {}, {})", BIDDER_EMAIL, DEMO_PASSWORD, BIDDER2_EMAIL, SELLER_EMAIL);
    }

    private boolean alreadySeeded() {
        return listingRepository.count() >= CATALOG_ALREADY_SEEDED_THRESHOLD;
    }

    /**
     * Deletes the whole demo catalog (listings, auctions, events, and every table that FKs onto
     * an auction/listing) in dependency order, ahead of a full reseed. Wallets, users, categories
     * and category filter defs are untouched.
     */
    private void wipeDemoCatalog() {
        bidRepository.deleteAllInBatch();
        bidEligibilityHoldRepository.deleteAllInBatch();
        saleProceedsHoldRepository.deleteAllInBatch();
        disputeRepository.deleteAllInBatch();
        listingImageRepository.deleteAllInBatch();
        listingAttributeRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
        listingRepository.deleteAllInBatch();
        auctionEventRepository.deleteAllInBatch();
        log.info("[dev-seed] wiped existing demo catalog ahead of full refresh");
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

    /**
     * Index 0-9 -> live (started recently, ends across the next ~1-2 days, staggered).
     * Index 10-19 -> upcoming (starts in the future).
     * Index 20-29 -> closed (both start and end already in the past).
     */
    private LocalDateTime[] bucketWindow(int i, LocalDateTime now) {
        int bucket = i / 10;
        int within = i % 10;
        if (bucket == 0) {
            return new LocalDateTime[]{now.minusMinutes(5 + within), now.plusHours(3 + within * 4L)};
        } else if (bucket == 1) {
            LocalDateTime start = now.plusHours(4 + within * 6L);
            return new LocalDateTime[]{start, start.plusDays(2 + within % 3)};
        } else {
            LocalDateTime end = now.minusHours(3 + within * 9L);
            return new LocalDateTime[]{end.minusDays(2 + within % 4), end};
        }
    }

    /** Year/fuel/transmission/odometer plus the 4 vehicle-detail fields VehicleDetailStrip reads. */
    private Map<String, String> vehicleAttrs(int idx, Category category, String city, String state) {
        String[] fuels = {"Petrol", "Diesel", "CNG", "Electric"};
        String[] transmissions = {"Manual", "Automatic"};
        int year = 2015 + (idx % 9);
        int km = 8000 + ((idx * 3721) % 92000);
        String stateCode = (state != null && state.length() >= 2) ? state.toUpperCase() : "DL";
        char l1 = (char) ('A' + idx % 26);
        char l2 = (char) ('A' + (idx * 5 + 3) % 26);
        String reg = stateCode + String.format("%02d", 1 + idx % 90) + l1 + l2 + String.format("%04d", 1000 + (idx * 137) % 9000);
        String chassis = "MA3ER" + String.format("%08d", 10_000_000 + (idx * 90_917) % 9_000_000);

        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("Year", String.valueOf(year));
        attrs.put("Fuel", fuels[idx % fuels.length]);
        attrs.put("Transmission", transmissions[idx % transmissions.length]);
        attrs.put("KM driven", String.valueOf(km));
        attrs.put("registrationNumber", reg);
        attrs.put("chassisNo", chassis);
        attrs.put("yardName", category.getName() + " Yard " + (1 + idx % 5));
        attrs.put("yardLocation", city + ", " + state);
        return attrs;
    }

    private Map<String, String> electronicsAttrs(int idx) {
        String[] rams = {"8 GB", "12 GB", "16 GB", "32 GB"};
        String[] storages = {"128 GB", "256 GB", "512 GB", "1 TB"};
        String[] screens = {"6.1\"", "6.7\"", "13.6\"", "15.6\"", "65\""};
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("RAM", rams[idx % rams.length]);
        attrs.put("Storage", storages[idx % storages.length]);
        attrs.put("Screen size", screens[idx % screens.length]);
        return attrs;
    }

    private Map<String, String> propertyAttrs(int idx) {
        String[] bedrooms = {"Studio", "1 BHK", "2 BHK", "3 BHK", "4 BHK"};
        String[] furnishing = {"Unfurnished", "Semi-furnished", "Furnished"};
        int area = 450 + (idx * 137) % 3600;
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("Bedrooms", bedrooms[idx % bedrooms.length]);
        attrs.put("Furnishing", furnishing[idx % furnishing.length]);
        attrs.put("Area (sqft)", String.valueOf(area));
        return attrs;
    }

    /** Plain (non-event) category: 30 items, no auction event, no subscription gate. */
    private void seedVehicles(User seller, Category category) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 30; i++) {
            String model = MASS_MARKET_VEHICLES[i];
            String[] loc = CITIES[i % CITIES.length];
            int year = 2015 + (i % 9);
            String title = year + " " + model + " (Owner Sale) — " + loc[0] + " Lot " + (1 + i);
            long price = 320_000 + (i * 23_500L);
            LocalDateTime[] w = bucketWindow(i, now);
            seedItem(seller, category, null, title, model.split(" ")[0], ItemCondition.USED,
                    loc[0], loc[1], price, w[0], w[1], SubscriptionTier.NONE, false,
                    vehicleAttrs(i, category, loc[0], loc[1]));
        }
    }

    private void seedProperties(User seller, Category category) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 30; i++) {
            String type = PROPERTY_ITEMS[i];
            String[] loc = CITIES[(i + 7) % CITIES.length];
            String title = type + " — " + loc[0] + " (Bank Auction) Lot " + (1 + i);
            long price = 1_800_000 + (i * 410_000L);
            LocalDateTime[] w = bucketWindow(i, now);
            seedItem(seller, category, null, title, null, ItemCondition.USED,
                    loc[0], loc[1], price, w[0], w[1], SubscriptionTier.NONE, false, propertyAttrs(i));
        }
    }

    private void seedElectronics(User seller, Category category) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 30; i++) {
            String model = ELECTRONICS_ITEMS[i];
            String[] loc = CITIES[(i + 3) % CITIES.length];
            ItemCondition condition = i % 5 == 0 ? ItemCondition.REFURBISHED : ItemCondition.NEW;
            String title = model + " (" + (condition == ItemCondition.NEW ? "Sealed" : "Refurbished") + ") — Lot " + (1 + i);
            long price = 25_000 + (i * 9_800L);
            LocalDateTime[] w = bucketWindow(i, now);
            seedItem(seller, category, null, title, model.split(" ")[0], condition,
                    loc[0], loc[1], price, w[0], w[1], SubscriptionTier.NONE, false, electronicsAttrs(i));
        }
    }

    private static final String[] BUCKET_ZONE_LABEL = {"Live", "Upcoming", "Closed"};

    /**
     * The 4 seller-events categories (Bank Vehicles / Insurance / Premium / Auto): 30 items each,
     * matching the CarTrade Exchange-style Events UI. A real bank/insurer auction never mixes vehicle
     * types under one listing (a branch with 4W and 2W repossessions runs two separate lots, not one)
     * — so instead of one event per Live/Upcoming/Closed bucket, items are grouped by
     * (bucket, Vehicle Type) and each distinct combination gets its own type-pure
     * {@link AuctionEvent}. {@code types[i]} (parallel to {@code models[i]}) drives both the "Vehicle
     * Type" attribute and this grouping. {@code insurance} adds salvage-specific attrs;
     * {@code assignTiers} cycles items across all 4 subscription tiers (used for Premium, so the
     * paywall UI still has real locked cards across every status bucket).
     */
    private void seedEventGroupedCategory(User seller, Category category, String[] models, String[] types,
                                          String eventNamePrefix, String titlePrefix, ItemCondition condition,
                                          long basePrice, long priceStep, boolean insurance, boolean assignTiers) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime[][] eventWindow = {
                {now.minusHours(3), now.plusDays(5)},
                {now.plusDays(2), now.plusDays(9)},
                {now.minusDays(14), now.minusHours(2)},
        };
        Map<String, AuctionEvent> eventsByKey = new LinkedHashMap<>();

        for (int i = 0; i < 30; i++) {
            String model = models[i];
            String vehicleType = types[i];
            int bucket = i / 10;
            String[] loc = CITIES[(i + 11) % CITIES.length];
            String eventKey = bucket + "|" + vehicleType;
            AuctionEvent event = eventsByKey.computeIfAbsent(eventKey, k -> auctionEventRepository.save(
                    AuctionEvent.builder().seller(seller).category(category)
                            .name(eventNamePrefix + " — " + BUCKET_ZONE_LABEL[bucket] + " Zone (" + vehicleType + ")")
                            .location("Pan India")
                            .startTime(eventWindow[bucket][0]).closingTime(eventWindow[bucket][1]).build()));

            String title = titlePrefix + " " + model + " — " + eventNamePrefix + " Lot " + (1 + i);
            long price = basePrice + (i * priceStep);
            LocalDateTime[] w = bucketWindow(i, now);

            Map<String, String> attrs = vehicleAttrs(i, category, loc[0], loc[1]);
            attrs.put("Vehicle Type", vehicleType);
            if (insurance) {
                attrs.put("Policy Type", i % 2 == 0 ? "Motor" : "Property");
                attrs.put("IDV", String.valueOf(150_000 + i * 21_000L));
                attrs.put("Claim No", "CLM-2026-" + (88_000 + i));
            }
            SubscriptionTier tier = assignTiers
                    ? SubscriptionTier.values()[i % SubscriptionTier.values().length]
                    : SubscriptionTier.NONE;

            seedItem(seller, category, event, title, model.split(" ")[0], condition,
                    loc[0], loc[1], price, w[0], w[1], tier, false, attrs);
        }
    }

    /**
     * 30 demo listings (10 live / 10 upcoming / 10 closed, same split as every other category) owned
     * by the Swipe Stock platform account with the {@code swipeStock} flag set. /swipe-stock reuses
     * BrowsePage filtered purely by that flag — same OPEN/SCHEDULED/CLOSED tabs as every other browse
     * view — so it needs the same per-status coverage. Alternates two categories to also prove the
     * page filters by the flag, not by category.
     */
    private void seedSwipeStockDemoData(Category electronics, Category vehicles) {
        User swipeStockSeller = platformAccountService.getOrCreateSwipeStockSeller();
        platformAccountService.getOrCreateSwipeStockCategory();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 30; i++) {
            String[] loc = CITIES[(i + 17) % CITIES.length];
            LocalDateTime[] w = bucketWindow(i, now);
            if (i % 2 == 0) {
                String model = ELECTRONICS_ITEMS[i % ELECTRONICS_ITEMS.length];
                String title = "Swipe Stock — " + model + " (Certified Refurbished) Lot " + (1 + i);
                long price = 20_000 + (i * 6_500L);
                seedItem(swipeStockSeller, electronics, null, title, model.split(" ")[0], ItemCondition.REFURBISHED,
                        loc[0], loc[1], price, w[0], w[1], SubscriptionTier.NONE, true, electronicsAttrs(i));
            } else {
                String model = MASS_MARKET_VEHICLES[i % MASS_MARKET_VEHICLES.length];
                String title = "Swipe Stock — Certified Pre-Owned " + model + " Lot " + (1 + i);
                long price = 280_000 + (i * 15_000L);
                seedItem(swipeStockSeller, vehicles, null, title, model.split(" ")[0], ItemCondition.REFURBISHED,
                        loc[0], loc[1], price, w[0], w[1], SubscriptionTier.NONE, true,
                        vehicleAttrs(i, vehicles, loc[0], loc[1]));
            }
        }
    }

    private void seedItem(User seller, Category category, AuctionEvent event, String title, String brand,
                          ItemCondition condition, String city, String state, long price,
                          LocalDateTime start, LocalDateTime end, SubscriptionTier tier, boolean swipeStock,
                          Map<String, String> attrs) {
        Listing listing = catalogService.createListing(seller, category.getId(), title,
                title + " — seeded demo listing.", brand, condition, city, state, null,
                BigDecimal.valueOf(price), attrs, swipeStock, tier);
        auctionService.createAuction(seller, listing.getId(), BigDecimal.valueOf(price), start, end,
                event != null ? event.getId() : null);
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
     * Attaches a small keyword-matched photo gallery (loremflickr.com, deterministic per title) to
     * every listing that doesn't have a cover image yet — i.e. every freshly seeded item. Generic by
     * design: keywords are derived from the listing's own brand + category, so it needs no per-title
     * lookup table and works for any listing, seeded or manually created.
     */
    private void seedGenericCoverImages(Map<java.util.UUID, String> categorySlugById) {
        int added = 0;
        for (Listing l : listingRepository.findAll()) {
            if (!listingImageRepository.findByListing_IdOrderBySortOrderAsc(l.getId()).isEmpty()) continue;
            String brandKeyword = l.getBrand() != null
                    ? l.getBrand().toLowerCase().replace(" ", "-").replace("\"", "") + ","
                    : "";
            // getCategory() is a lazy proxy and there's no open Hibernate session at boot time outside
            // a @Transactional call — go through the ID (always available on the proxy) instead of the
            // slug (which would trigger a LazyInitializationException).
            String slug = categorySlugById.getOrDefault(l.getCategory().getId(), "auction");
            String keywords = brandKeyword + slug;
            List<String> urls = demoGallery(l.getTitle(), keywords, 3);
            int order = 0;
            for (String url : urls) {
                listingImageRepository.save(ListingImage.builder()
                        .listing(l).url(url).sortOrder(order).cover(order == 0).build());
                order++;
                added++;
            }
        }
        if (added > 0) log.info("[dev-seed] attached {} demo listing image(s)", added);
    }

    /**
     * {@code count} distinct, keyword-relevant stock photos for a demo listing — pinned to
     * deterministic loremflickr.com "lock" seeds (derived from the title) so the result is stable
     * across restarts instead of changing every run.
     */
    private List<String> demoGallery(String title, String keywords, int count) {
        // Encode each term so a space (e.g. "range rover") can't produce a malformed, silently-
        // broken-image URL — loremflickr keeps the comma separators, just not raw spaces.
        String encodedKeywords = java.util.Arrays.stream(keywords.split(","))
                .map(k -> java.net.URLEncoder.encode(k.trim(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
        List<String> urls = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            long lock = Math.abs((long) (title + "#" + i).hashCode());
            urls.add("https://loremflickr.com/800/600/" + encodedKeywords + "?lock=" + lock);
        }
        return urls;
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
                    // Pre-paid so demo browsing/bidding isn't blocked by the registration-fee wall.
                    .registrationFeePaid(true)
                    .userRefNumber(ref).build());
            walletService.topUp(u, new BigDecimal("100000.00"));
            return u;
        });
    }

    private void getOrCreateAdmin() {
        Admin admin = adminRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> adminRepository.save(Admin.builder()
                .firstName("Swipe").lastName("Admin").email(ADMIN_EMAIL).mobileNumber("9000000099")
                .password(passwordEncoder.encode(DEMO_PASSWORD)).role(Role.ADMIN)
                .adminRole(AdminRole.SUPER_ADMIN).active(true).build()));

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
