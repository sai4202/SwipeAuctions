package com.swipeauctions.auth.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class OtpGenerator {

    private final SecureRandom random =
            new SecureRandom();

    public String generateOtp() {

        return String.valueOf(
                100000 + random.nextInt(900000)
        );
    }
}