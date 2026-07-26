package com.swipeauctions.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import com.swipeauctions.common.entity.BaseEntity;

import java.time.LocalDateTime;

/** One-shot OTP for the "Login with OTP" flow. Kept separate from {@link OtpVerification}, which
 *  tracks pending registration-verification state, not a single-use login code. */
@Entity
@Table(name = "login_otps")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginOtp extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 6)
    private String otp;

    @Column(nullable = false)
    private LocalDateTime expiry;

    @Builder.Default
    @Column(nullable = false)
    private Boolean consumed = false;

    private LocalDateTime lastSentAt;
}
