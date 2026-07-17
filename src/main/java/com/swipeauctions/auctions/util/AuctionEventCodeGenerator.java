package com.swipeauctions.auctions.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuctionEventCodeGenerator {
    public String generateEventCode() {

        return "SWAEVT" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();
    }
}
