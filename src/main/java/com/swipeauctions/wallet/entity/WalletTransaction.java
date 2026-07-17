package com.swipeauctions.wallet.entity;

import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.wallet.enums.WalletTxnType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Append-only ledger entry. Wallet balances are a cache that must reconcile against these rows. */
@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wallet_txn_wallet", columnList = "wallet_id"),
        @Index(name = "idx_wallet_txn_reference", columnList = "reference_type, reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletTxnType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 255)
    private String referenceId;
}
