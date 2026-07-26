package com.swipeauctions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Single deployable entry point for SwipeAuctions.
 *
 * <p>Component and entity scanning default to the {@code com.swipeauctions} base package,
 * which covers every domain package (auth, catalog, auction, wallet, bidding, ...).
 *
 * <p>{@link EnableAsync} lets user-facing notification emails (bid placed, outbid, login, …)
 * be sent off the request thread so SMTP latency or failures never block bidding or login.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SwipeAuctionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwipeAuctionsApplication.class, args);
    }
}
