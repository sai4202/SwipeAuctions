package com.swipeauctions.bidding.entity;

import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/** A bidder banned from one specific auction by an admin — lighter-weight than the account-wide
 *  suspend, e.g. for a single mistaken/disputed bid. Their existing {@link Bid} rows are left
 *  untouched (history), but {@code BidService} excludes them from that auction's leader/ranking and
 *  rejects any further bids from them on it (see {@code BidService#placeBid}/{@code #disqualifyBidder}). */
@Entity
@Table(name = "auction_disqualifications", uniqueConstraints = @UniqueConstraint(columnNames = {"auction_id", "bidder_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionDisqualification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(nullable = false, length = 500)
    private String reason;
}
