package com.swipeauctions.user.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.enums.Role;

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
}