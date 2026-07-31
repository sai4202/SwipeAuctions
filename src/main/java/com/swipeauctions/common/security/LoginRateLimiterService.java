package com.swipeauctions.common.security;

import com.swipeauctions.common.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP login throttle, independent of the existing per-account lockout in
 * AdminLoginSecurityService / UserLoginSecurityService. Per-account lockout alone
 * doesn't stop distributed credential stuffing — one IP cycling through many
 * different emails, none of which individually hits the 5-attempt account lock.
 *
 * In-memory sliding window, keyed by client IP + a caller-supplied bucket name
 * (so admin and user login are throttled independently). Fine for a single
 * instance; a horizontally-scaled deployment would need a shared store (Redis)
 * instead of this local map.
 */
@Component
@Slf4j
public class LoginRateLimiterService {

    private static class Bucket {
        int count;
        Instant windowStart = Instant.now();
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Records an attempt from the given key and throws once the limit is exceeded
     * within the rolling window. Call this before doing any DB lookup for the
     * login attempt so a flood can't even reach the database.
     */
    public void checkAndRecord(String bucketName, String ip, int maxAttempts, Duration window) {

        String key = bucketName + ":" + ip;

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        synchronized (bucket) {

            Instant now = Instant.now();

            if (Duration.between(bucket.windowStart, now).compareTo(window) > 0) {
                bucket.windowStart = now;
                bucket.count = 0;
            }

            bucket.count++;

            if (bucket.count > maxAttempts) {
                log.warn("Login rate limit exceeded for bucket={} ip={}", bucketName, ip);
                throw new TooManyRequestsException(
                        "Too many login attempts from this network. Please try again later.");
            }
        }
    }

    // Evicts stale buckets so this map doesn't grow unbounded over the app's lifetime.
    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void evictStaleBuckets() {

        Instant cutoff = Instant.now().minus(Duration.ofHours(1));

        buckets.entrySet().removeIf(entry -> entry.getValue().windowStart.isBefore(cutoff));
    }
}
