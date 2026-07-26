package com.swipeauctions.wallet.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.wallet.enums.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One seller/dealer payout attempt: wallet debited immediately, paid out via a Stripe Transfer. */
@Entity
@Table(name = "wallet_withdrawals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletWithdrawal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "stripe_transfer_id", unique = true)
    private String stripeTransferId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalStatus status = WithdrawalStatus.PENDING;

    private LocalDateTime completedAt;
}
