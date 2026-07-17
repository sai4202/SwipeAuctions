package com.swipeauctions.wallet.entity;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.enums.HoldStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The per-auction refundable deposit that gates bidding ("real vs random bidder"). One per
 * (auction, bidder); ACTIVE while held, then RELEASED (loser) or CAPTURED (winner) at close.
 */
@Entity
@Table(
        name = "bid_eligibility_holds",
        uniqueConstraints = @UniqueConstraint(columnNames = {"auction_id", "bidder_id"}),
        indexes = @Index(name = "idx_hold_auction_status", columnList = "auction_id, status")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BidEligibilityHold extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldStatus status = HoldStatus.ACTIVE;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
