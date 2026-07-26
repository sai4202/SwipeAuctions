package com.swipeauctions.event.entity;

import com.swipeauctions.catalog.entity.Category;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A seller-created grouping of auctions under one banner (e.g. a bank's regional asset sale or an
 * insurer's salvage sale), scoped to a single category. Browsing lists events first; entering one
 * shows the auctions attached to it via {@link com.swipeauctions.auction.entity.Auction#getEvent()}.
 */
@Entity
@Table(name = "auction_events", indexes = {
        @Index(name = "idx_auction_events_seller", columnList = "seller_id"),
        @Index(name = "idx_auction_events_category", columnList = "category_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String location;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "closing_time", nullable = false)
    private LocalDateTime closingTime;
}
