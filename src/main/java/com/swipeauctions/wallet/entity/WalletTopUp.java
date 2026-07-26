package com.swipeauctions.wallet.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.wallet.enums.TopUpStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One buyer wallet-funding attempt via a Stripe PaymentIntent. Credited on the success webhook. */
@Entity
@Table(name = "wallet_topups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTopUp extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "stripe_payment_intent_id", nullable = false, unique = true)
    private String stripePaymentIntentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopUpStatus status = TopUpStatus.PENDING;

    private LocalDateTime completedAt;
}
