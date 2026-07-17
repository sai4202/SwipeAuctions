package com.swipeauctions.auctions.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LotReferenceGenerator {
    public String generateLotNumber() {

        return "SWALOT" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();
    }
}
