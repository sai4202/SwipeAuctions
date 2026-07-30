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

    @Builder.Default
    @Column(name = "registration_fee_paid", nullable = false)
    private Boolean registrationFeePaid = false;

    @Column(name = "registration_fee_paid_at")
    private LocalDateTime registrationFeePaidAt;

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

    // ---- Razorpay payouts ----

    /** Razorpay Contact id — created once a seller/dealer submits payout bank details. */
    @Column(name = "razorpay_contact_id")
    private String razorpayContactId;

    /** Razorpay Fund Account id (their bank account) — sellers/dealers need this to receive
     *  wallet withdrawals. No OAuth-redirect onboarding like Stripe Connect: bank details are
     *  submitted directly in-app and turned into a Contact + Fund Account server-side. */
    @Column(name = "razorpay_fund_account_id")
    private String razorpayFundAccountId;

    @Builder.Default
    @Column(name = "razorpay_payouts_enabled", nullable = false)
    private Boolean razorpayPayoutsEnabled = false;

    // ---- Subscription tier (buyer content access) ----

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 20)
    private SubscriptionTier subscriptionTier = SubscriptionTier.NONE;

    @Column(name = "subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;
}