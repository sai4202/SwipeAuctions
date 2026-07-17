package com.swipeauctions.auction.entity;

import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.catalog.entity.Listing;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An English (ascending-bid) auction over one {@link Listing}, with anti-snipe auto-extend.
 * {@code basePrice} is both the starting price and the required wallet-deposit hold amount.
 */
@Entity
@Table(name = "auctions", indexes = {
        @Index(name = "idx_auctions_status", columnList = "status"),
        @Index(name = "idx_auctions_current_end_time", columnList = "current_end_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auction extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, unique = true)
    private Listing listing;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "current_end_time", nullable = false)
    private LocalDateTime currentEndTime;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status = AuctionStatus.SCHEDULED;

    @Column(name = "current_highest_bid", precision = 12, scale = 2)
    private BigDecimal currentHighestBid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_winner_id")
    private User currentWinner;

    @Builder.Default
    @Column(name = "extension_count", nullable = false)
    private int extensionCount = 0;

    @Version
    @Column(nullable = false)
    private Long version;
}
