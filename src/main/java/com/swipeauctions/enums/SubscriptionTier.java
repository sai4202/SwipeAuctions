package com.swipeauctions.enums;

/**
 * Buyer access tier, cheapest to richest. {@code level} is explicit (not bare ordinal) so tier
 * comparisons stay correct even if a tier is inserted between two existing ones later.
 */
public enum SubscriptionTier {
    NONE(0),
    SILVER(1),
    GOLD(2),
    DIAMOND(3);

    private final int level;

    SubscriptionTier(int level) {
        this.level = level;
    }

    /** True if this tier grants access to content gated at {@code required}. */
    public boolean meetsRequirement(SubscriptionTier required) {
        return this.level >= required.level;
    }
}
