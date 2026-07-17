package com.swipeauctions.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.swipeauctions.common.entity.BaseEntity;

import java.time.LocalDateTime;

// Stores email/mobile update requests pending OTP verification
@Entity
@Table(name = "user_update_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest extends BaseEntity {

    // User requesting the update
    @OneToOne
    @JoinColumn(
            name = "user_id",
            unique = true
    )
    private User user;

    // New email waiting for verification
    private String newEmail;

    // Email verification OTP
    private String emailOtp;

    // Email OTP expiry time
    private LocalDateTime emailOtpExpiry;

    // New mobile number waiting for verification
    private String newMobileNumber;

    // Mobile verification OTP
    private String mobileOtp;

    // Mobile OTP expiry time
    private LocalDateTime mobileOtpExpiry;

    // Email update verification status
    @Builder.Default
    private Boolean emailVerified = false;

    // Mobile update verification status
    @Builder.Default
    private Boolean mobileVerified = false;

    // Tracks last OTP sent time for rate limiting
    private LocalDateTime emailOtpSentAt;

    private LocalDateTime mobileOtpSentAt;

    private LocalDateTime requestedAt;

    private LocalDateTime verifiedAt;
}