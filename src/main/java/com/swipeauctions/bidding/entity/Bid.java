package com.swipeauctions.bidding.entity;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A single ascending bid placed on an auction. */
@Entity
@Table(name = "bids", indexes = {
        @Index(name = "idx_bids_auction_amount", columnList = "auction_id, amount"),
        @Index(name = "idx_bids_auction_placed", columnList = "auction_id, placed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bid extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "placed_at", nullable = false)
    private LocalDateTime placedAt;
}
