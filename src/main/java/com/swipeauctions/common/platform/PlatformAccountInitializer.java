package com.swipeauctions.common.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the Swipe Stock platform seller account + category exist on every boot, in every
 * environment — unlike {@code DevDataSeeder}, this is real platform data, not dev-only demo data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountInitializer implements CommandLineRunner {

    private final PlatformAccountService platformAccountService;

    @Override
    public void run(String... args) {
        platformAccountService.getOrCreateSwipeStockSeller();
        platformAccountService.getOrCreateSwipeStockCategory();
        log.info("[platform-init] Swipe Stock seller + category ready");
    }
}
