package com.swipeauctions.common.dev;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.catalog.enums.ItemCondition;
import com.swipeauctions.auction.service.AuctionService;
import com.swipeauctions.catalog.service.CatalogService;
import com.swipeauctions.enums.Role;
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

/**
 * Seeds demo data for local testing — only under the "dev" profile
 * ({@code --spring.profiles.active=dev}). Creates a pre-verified seller and bidder (so login works
 * without OTP), funds the bidder's wallet, and stands up one OPEN auction.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements CommandLineRunner {

    private static final String SELLER_EMAIL = "seller@swipeauctions.test";
    private static final String BIDDER_EMAIL = "bidder@swipeauctions.test";
    private static final String DEMO_PASSWORD = "Test@1234";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;
    private final CatalogService catalogService;
    private final AuctionService auctionService;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(SELLER_EMAIL)) {
            log.info("[dev-seed] demo data already present; skipping");
            return;
        }

        User seller = createUser(SELLER_EMAIL, "9000000001", "DEV-SELLER-0001");
        User bidder = createUser(BIDDER_EMAIL, "9000000002", "DEV-BIDDER-0001");

        walletService.topUp(bidder, new BigDecimal("100000.00"));

        Category electronics = catalogService.createCategory("Electronics", "electronics", null);
        Listing listing = catalogService.createListing(seller, electronics.getId(),
                "MacBook Pro 14\" (2023)", "Lightly used, boxed.", "Apple",
                ItemCondition.USED, "Bengaluru", "KA", "560001", new BigDecimal("5000.00"));

        Auction auction = auctionService.createAuction(seller, listing.getId(),
                new BigDecimal("5000.00"),
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1));

        log.info("[dev-seed] seller={} bidder={} password={}", SELLER_EMAIL, BIDDER_EMAIL, DEMO_PASSWORD);
        log.info("[dev-seed] category={} listing={} auction={} (OPEN, base 5000)",
                electronics.getId(), listing.getId(), auction.getId());
    }

    private User createUser(String email, String mobile, String ref) {
        return userRepository.save(User.builder()
                .role(Role.USER)
                .email(email)
                .mobileNumber(mobile)
                .password(passwordEncoder.encode(DEMO_PASSWORD))
                .active(true)
                .emailVerified(true)
                .mobileVerified(true)
                .userRefNumber(ref)
                .build());
    }
}
