package com.swipeauctions.auth.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserReferenceNumGenerator {

    public String generateUserReferenceNumber() {

        return "SWAUSR" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();
    }
}
