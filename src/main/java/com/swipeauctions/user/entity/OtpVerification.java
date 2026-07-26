package com.swipeauctions.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import com.swipeauctions.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "otpVerifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerification extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 6)
    private String emailOtp;

    @Column(length = 6)
    private String mobileOtp;

    @Builder.Default
    private Boolean emailVerified = false;

    @Builder.Default
    private Boolean mobileVerified = false;

    private LocalDateTime emailOtpExpiry;

    private LocalDateTime mobileOtpExpiry;

    private LocalDateTime lastOtpSentAt;

    /** Wrong-guess counters — locked out (OTP cleared, resend required) past a threshold. */
    @Builder.Default
    @Column(nullable = false)
    private int emailOtpAttempts = 0;

    @Builder.Default
    @Column(nullable = false)
    private int mobileOtpAttempts = 0;

}