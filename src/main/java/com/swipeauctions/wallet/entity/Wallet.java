package com.swipeauctions.wallet.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Internal per-user wallet. {@code availableBalance} can be spent/held; {@code heldBalance} is the
 * sum of active deposit holds. Both must reconcile against the summed {@link WalletTransaction} log.
 */
@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(name = "available_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "held_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal heldBalance = BigDecimal.ZERO;
}
