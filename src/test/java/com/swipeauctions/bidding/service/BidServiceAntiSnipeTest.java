package com.swipeauctions.bidding.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for the anti-snipe extension decision, no Spring context / no mocks. */
class BidServiceAntiSnipeTest {

    private static final long WINDOW_SECONDS = 30;
    private static final long EXTENSION_SECONDS = 30;
    private static final int MAX_EXTENSIONS = 10;

    @Test
    void extendsWhenBidLandsInsideTheWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(10); // 10s remaining, inside the 30s window

        assertThat(BidService.shouldExtend(now, endTime, 0, WINDOW_SECONDS, MAX_EXTENSIONS)).isTrue();
    }

    @Test
    void doesNotExtendWhenBidLandsOutsideTheWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(31); // just outside the 30s window

        assertThat(BidService.shouldExtend(now, endTime, 0, WINDOW_SECONDS, MAX_EXTENSIONS)).isFalse();
    }

    @Test
    void boundaryAtExactlyTheWindowStillExtends() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(WINDOW_SECONDS); // exactly at the boundary, inclusive

        assertThat(BidService.shouldExtend(now, endTime, 0, WINDOW_SECONDS, MAX_EXTENSIONS)).isTrue();
    }

    @Test
    void doesNotExtendOnceMaxExtensionsReached() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(5);

        assertThat(BidService.shouldExtend(now, endTime, MAX_EXTENSIONS, WINDOW_SECONDS, MAX_EXTENSIONS)).isFalse();
    }

    @Test
    void extendsUpToOneBelowTheMaxExtensionsCap() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(5);

        assertThat(BidService.shouldExtend(now, endTime, MAX_EXTENSIONS - 1, WINDOW_SECONDS, MAX_EXTENSIONS)).isTrue();
    }

    @Test
    void extendPushesTheEndTimeOutByExtensionSeconds() {
        LocalDateTime endTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

        assertThat(BidService.extend(endTime, EXTENSION_SECONDS)).isEqualTo(endTime.plusSeconds(EXTENSION_SECONDS));
    }
}
