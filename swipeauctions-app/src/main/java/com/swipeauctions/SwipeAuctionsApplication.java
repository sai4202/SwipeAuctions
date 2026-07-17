package com.swipeauctions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single deployable entry point for the SwipeAuctions modular monolith.
 *
 * <p>Component and entity scanning default to the {@code com.swipeauctions} base package,
 * which covers every {@code swipeauctions-*} module on the classpath.
 */
@SpringBootApplication
public class SwipeAuctionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwipeAuctionsApplication.class, args);
    }
}
