package com.swipeauctions.wallet.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.enums.PaymentPurpose;
import com.swipeauctions.wallet.enums.TopUpStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One Razorpay Order — wallet top-up, registration fee, or subscription purchase (see
 * {@link PaymentPurpose}). Settled (wallet credited / flag flipped / tier granted) once Razorpay
 * confirms payment, via either the client-side verify call or the webhook, whichever lands first.
 */
@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "razorpay_order_id", nullable = false, unique = true)
    private String razorpayOrderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentPurpose purpose = PaymentPurpose.WALLET_TOPUP;

    /** Purpose-specific extra data — e.g. {@code "GOLD:MONTHLY"} for a subscription order. Unused
     *  (null) for a plain wallet top-up. */
    @Column(length = 100)
    private String metadata;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopUpStatus status = TopUpStatus.PENDING;

    private LocalDateTime completedAt;
}
