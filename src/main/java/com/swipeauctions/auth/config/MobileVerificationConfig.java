package com.swipeauctions.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether mobile/SMS OTP verification is required at registration and login. Defaults to false —
 * {@code LoggingSmsServiceImpl} is a logging-only stub with no real SMS provider wired up yet, so
 * requiring it would make registration impossible. Flip via {@code AUTH_MOBILE_VERIFICATION_REQUIRED}
 * once a real SmsService is in place; no code change needed on either side of that flip.
 */
@Component
public class MobileVerificationConfig {

    @Value("${auth.mobile-verification-required:false}")
    private boolean required;

    public boolean isRequired() {
        return required;
    }
}
