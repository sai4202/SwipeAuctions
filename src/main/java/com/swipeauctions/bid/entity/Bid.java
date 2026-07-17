package com.swipeauctions.bid.entity;

import jakarta.persistence.*;
import lombok.*;
import com.swipeauctions.auctions.entity.AuctionItem;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bids",
        indexes = {
                @Index(name = "idx_bid_amount", columnList = "bid_amount"),
                @Index(name = "idx_bid_time", columnList = "bid_time")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bid extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "auction_item_id",
            nullable = false
    )
    private AuctionItem auctionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "bidder_id",
            nullable = false
    )
    private User bidder;

    @Column(
            name = "bid_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal bidAmount;

    @Column(
            name = "bid_time",
            nullable = false
    )
    private LocalDateTime bidTime;

    @Column(
            name = "ip_address",
            length = 50
    )
    private String ipAddress;

    @Column(
            name = "device_information",
            length = 256
    )
    private String deviceInformation;

    @Column(
            name = "bid_source",
            length = 30
    )
    private String bidSource;

}