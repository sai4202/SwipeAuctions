package com.swipeauctions.referral.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/** One row per successful signup attributed to a referrer — captured client-side right after the
 *  new account is verified and signed in (see ReferralController), never during the registration
 *  transaction itself, so a referral-capture failure can never break signup. A user can be referred
 *  at most once ({@code referred} is unique); a referrer can appear many times. */
@Entity
@Table(name = "referrals", uniqueConstraints = @UniqueConstraint(columnNames = "referred_id"),
        indexes = @Index(name = "idx_referrals_referrer", columnList = "referrer_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Referral extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referred_id", nullable = false)
    private User referred;
}
