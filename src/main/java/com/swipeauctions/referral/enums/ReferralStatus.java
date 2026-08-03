package com.swipeauctions.referral.enums;

/** Lifecycle of a referral's reward. PENDING until the referred user makes a single top-up that
 *  meets the admin-configured minimum deposit; SUCCESSFUL once the bonus has been credited. */
public enum ReferralStatus {
    PENDING,
    SUCCESSFUL
}
