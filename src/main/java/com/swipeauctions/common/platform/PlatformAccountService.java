package com.swipeauctions.common.platform;

import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.catalog.repository.CategoryRepository;
import com.swipeauctions.catalog.service.CatalogService;
import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.enums.Role;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the platform's own "Swipe Stock" seller identity — a real, ordinary User account (not a
 * special role) that lists inventory the same way any seller does via the existing Sell flow.
 * The /swipe-stock page is just the auctions browse page filtered to this one seller's listings.
 * Idempotent and NOT dev-profile-gated: the account and its category must exist in every
 * environment, not just local seed data.
 */
@Service
@RequiredArgsConstructor
public class PlatformAccountService {

    public static final String SWIPE_STOCK_SELLER_EMAIL = "stock@swipeauctions.com";
    public static final String SWIPE_STOCK_CATEGORY_SLUG = "swipe-stock";

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogService catalogService;
    private final PasswordEncoder passwordEncoder;

    // No fallback default on purpose: this account is created in every environment (not dev-gated),
    // so a hardcoded default would mean every deployment that forgets to set this env var ships a
    // real, loginable seller account with a password visible in the public source tree. Missing the
    // property fails Spring context startup instead of silently using a known password.
    @Value("${SWIPE_STOCK_SELLER_PASSWORD}")
    private String sellerPassword;

    @Transactional
    public User getOrCreateSwipeStockSeller() {
        return userRepository.findByEmail(SWIPE_STOCK_SELLER_EMAIL).orElseGet(() -> userRepository.save(User.builder()
                .role(Role.USER)
                .email(SWIPE_STOCK_SELLER_EMAIL)
                .mobileNumber("9000000098")
                .password(passwordEncoder.encode(sellerPassword))
                .active(true)
                .emailVerified(true)
                .mobileVerified(true)
                .kycCompleted(true)
                .kycStatus(KycStatus.APPROVED)
                .userRefNumber("SWIPE-STOCK-0001")
                .build()));
    }

    @Transactional
    public Category getOrCreateSwipeStockCategory() {
        return categoryRepository.findBySlug(SWIPE_STOCK_CATEGORY_SLUG)
                .orElseGet(() -> catalogService.createCategory("Swipe Stock", SWIPE_STOCK_CATEGORY_SLUG, null));
    }
}
