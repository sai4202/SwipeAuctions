package com.swipeauctions.user.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.enums.Role;
import com.swipeauctions.enums.SubscriptionTier;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String mobileNumber;

    @Column(nullable = false)
    private String password;

    @Builder.Default
    private Boolean active = false;

    @Builder.Default
    private Boolean emailVerified = false;

    @Builder.Default
    private Boolean mobileVerified = false;

    @Builder.Default
    private Boolean kycCompleted = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private KycStatus kycStatus =
            KycStatus.NOT_SUBMITTED;

    @Column(
            name = "user_reference_number",
            nullable = false,
            unique = true,
            length = 20
    )
    private String userRefNumber;

    // Audit Fields
    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private String lastLoginDevice;

    private LocalDateTime passwordChangedAt;

    @Builder.Default
    private Boolean accountNonLocked = true;

    @Builder.Default
    private Integer failedLoginAttempts = 0;

    private LocalDateTime lockedUntil;

    // ---- Stripe (Phase 2) ----

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    /** Connect Express account id — sellers/dealers need this to receive wallet withdrawals. */
    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    @Builder.Default
    @Column(name = "stripe_charges_enabled", nullable = false)
    private Boolean stripeChargesEnabled = false;

    @Builder.Default
    @Column(name = "stripe_payouts_enabled", nullable = false)
    private Boolean stripePayoutsEnabled = false;

    // ---- Subscription tier (buyer content access) ----

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 20)
    private SubscriptionTier subscriptionTier = SubscriptionTier.NONE;

    @Column(name = "subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;
}