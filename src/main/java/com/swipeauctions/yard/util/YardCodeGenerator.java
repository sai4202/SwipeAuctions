package com.swipeauctions.yard.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class YardCodeGenerator {
    public String generateYardCode() {

        return "SWAYARD" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();
    }
}
