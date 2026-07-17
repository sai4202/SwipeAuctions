package com.swipeauctions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Single deployable entry point for SwipeAuctions.
 *
 * <p>Component and entity scanning default to the {@code com.swipeauctions} base package,
 * which covers every domain package (auth, catalog, auction, wallet, bidding, ...).
 */
@SpringBootApplication
@EnableScheduling
public class SwipeAuctionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwipeAuctionsApplication.class, args);
    }
}
