package com.swipeauctions.referral.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.referral.enums.ReferralStatus;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row per successful signup attributed to a referrer — captured client-side right after the
 *  new account is verified and signed in (see ReferralController), never during the registration
 *  transaction itself, so a referral-capture failure can never break signup. A user can be referred
 *  at most once ({@code referred} is unique); a referrer can appear many times.
 *
 *  <p>Starts {@code PENDING}; becomes {@code SUCCESSFUL} (see {@code ReferralService#onTopUp})
 *  once the referred user makes a single top-up meeting the admin-configured minimum deposit,
 *  at which point {@code rewardAmount}/{@code creditedAt} are set once and never change again —
 *  {@code rewardAmount} snapshots the bonus at credit time so a later admin edit to the bonus
 *  amount doesn't rewrite already-paid history. */
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReferralStatus status = ReferralStatus.PENDING;

    @Column(name = "reward_amount", precision = 12, scale = 2)
    private BigDecimal rewardAmount;

    @Column(name = "credited_at")
    private LocalDateTime creditedAt;
}
