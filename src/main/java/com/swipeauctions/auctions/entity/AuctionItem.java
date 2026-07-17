package com.swipeauctions.auctions.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.auctions.enums.LotStatus;
import com.swipeauctions.bid.entity.Bid;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.vehicle.entity.Vehicle;
import com.swipeauctions.yard.entity.Yard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "auction_items",
        indexes = {
                @Index(name = "idx_lot_number", columnList = "lot_number"),
                @Index(name = "idx_lot_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionItem extends BaseEntity {

    @Column(
            name = "lot_number",
            nullable = false,
            unique = true,
            length = 20
    )
    private String lotNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "auction_event_id",
            nullable = false
    )
    private AuctionEvent auctionEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "vehicle_id",
            nullable = false
    )
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "yard_id",
            nullable = false
    )
    private Yard yard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "winner_id"
    )
    private User winner;

    @Column(
            name = "starting_bid",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal startingBid;

    @Column(
            name = "reserve_price",
            precision = 19,
            scale = 2
    )
    private BigDecimal reservePrice;

//    @Column(
//            name = "buy_now_price",
//            precision = 19,
//            scale = 2
//    )
//    private BigDecimal buyNowPrice;

    @Column(
            name = "minimum_increment",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal minimumIncrement;

    @Column(
            name = "current_highest_bid",
            precision = 19,
            scale = 2
    )
    private BigDecimal currentHighestBid;

    @Column(
            name = "winning_amount",
            precision = 19,
            scale = 2
    )
    private BigDecimal winningAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LotStatus status;

    @Builder.Default
    @Column(nullable = false)
    private Boolean relisted = false;

    @Builder.Default
    @Column(name = "relist_count", nullable = false)
    private Integer relistCount = 0;

    @Builder.Default
    @Column(name = "bid_count", nullable = false)
    private Integer bidCount = 0;

    @Builder.Default
    @Column(name = "watch_count", nullable = false)
    private Integer watchCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @OneToMany(
            mappedBy = "auctionItem",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Bid> bids = new ArrayList<>();

}