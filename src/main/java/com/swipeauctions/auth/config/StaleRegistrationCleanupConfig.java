package com.swipeauctions.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Unverified registrations (active=false, stuck at the OTP stage) permanently occupy the unique
 * email/mobileNumber/userRefNumber slots on `users` since there's no expiry otherwise. This backs
 * StaleRegistrationCleanupScheduler, which purges them once older than threshold-hours, freeing
 * those slots for a fresh registration attempt. `enabled` is a kill-switch to pause the purge
 * without a redeploy.
 */
@Component
public class StaleRegistrationCleanupConfig {

    @Value("${auth.stale-registration-cleanup.enabled:true}")
    private boolean enabled;

    @Value("${auth.stale-registration-cleanup.threshold-hours:24}")
    private long thresholdHours;

    public boolean isEnabled() {
        return enabled;
    }

    public long getThresholdHours() {
        return thresholdHours;
    }
}
