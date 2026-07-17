package com.swipeauctions.user.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.enums.Gender;
import com.swipeauctions.enums.KycStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycVerification extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true
    )
    private User user;

    @Column(nullable = false)
    private String fullName;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(length = 1000)
    private String address;

    private String city;

    private String state;

    private String pincode;

    private String aadhaarMasked;

    private String panNumberMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private KycStatus status = KycStatus.NOT_SUBMITTED;

    @Column(length = 1000)
    private String remarks;

    private LocalDateTime submittedAt;

    private LocalDateTime verifiedAt;
}