package com.swipeauctions.enums;

import java.time.LocalDateTime;

public enum BillingCycle {
    MONTHLY,
    QUARTERLY,
    HALF_YEARLY,
    YEARLY;

    /** Expiry timestamp for a subscription starting now under this cycle. */
    public LocalDateTime expiryFrom(LocalDateTime start) {
        return switch (this) {
            case MONTHLY -> start.plusMonths(1);
            case QUARTERLY -> start.plusMonths(3);
            case HALF_YEARLY -> start.plusMonths(6);
            case YEARLY -> start.plusMonths(12);
        };
    }
}
