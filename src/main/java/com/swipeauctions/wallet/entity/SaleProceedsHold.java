package com.swipeauctions.wallet.entity;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.enums.ProceedsStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A seller's sale proceeds for one auction, escrowed in their wallet's held balance rather than
 * paid out instantly. Auto-released to available balance after {@code dispute.proceeds-hold-hours}
 * unless a dispute is open on the auction; an admin can release or refund it early on resolution.
 * One per auction (a listing can only be auctioned once — see {@link Auction}'s unique listing FK).
 */
@Entity
@Table(
        name = "sale_proceeds_holds",
        uniqueConstraints = @UniqueConstraint(columnNames = "auction_id"),
        indexes = @Index(name = "idx_proceeds_status", columnList = "status")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleProceedsHold extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false, unique = true)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProceedsStatus status = ProceedsStatus.ACTIVE;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
