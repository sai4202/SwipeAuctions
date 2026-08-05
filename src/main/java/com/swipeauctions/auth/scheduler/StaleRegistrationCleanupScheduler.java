package com.swipeauctions.auth.scheduler;

import com.swipeauctions.auth.config.StaleRegistrationCleanupConfig;
import com.swipeauctions.auth.helper.UserRegistrationHelperService;
import com.swipeauctions.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Periodically purges unverified (active=false) registrations older than the configured
 *  threshold, so their email/mobileNumber/userRefNumber unique slots free up for re-registration. */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleRegistrationCleanupScheduler {

    private final UserRegistrationHelperService registrationHelperService;

    private final StaleRegistrationCleanupConfig config;

    @Scheduled(fixedDelayString = "${auth.stale-registration-cleanup.interval-ms:3600000}")
    public void purgeStaleRegistrations() {
        if (!config.isEnabled()) {
            return;
        }

        List<User> candidates = registrationHelperService.findStaleUnverifiedUsers(config.getThresholdHours());
        int purged = 0;

        // Each row is purged through its own call (own transaction) so one bad row (e.g. an
        // unexpected dependent row tripping a FK constraint) can't block every other stale
        // account from being purged in the same tick.
        for (User u : candidates) {
            try {
                if (registrationHelperService.purgeIfStillUnverified(u.getId(), u.getEmail())) {
                    purged++;
                }
            } catch (Exception e) {
                log.warn("Failed to purge stale unverified registration {} ({}): {}",
                        u.getId(), u.getEmail(), e.getMessage());
            }
        }

        if (!candidates.isEmpty()) {
            log.info("Stale registration cleanup: purged {}/{} unverified accounts older than {}h",
                    purged, candidates.size(), config.getThresholdHours());
        }
    }
}
