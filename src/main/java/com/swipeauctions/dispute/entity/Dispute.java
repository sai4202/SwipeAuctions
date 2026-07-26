package com.swipeauctions.dispute.entity;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.common.entity.BaseEntity;
import com.swipeauctions.dispute.enums.DisputeStatus;
import com.swipeauctions.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A user-raised issue tied to an auction, tracked by admins through to resolution. */
@Entity
@Table(name = "disputes", indexes = {
        @Index(name = "idx_disputes_status", columnList = "status"),
        @Index(name = "idx_disputes_auction", columnList = "auction_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raised_by_id", nullable = false)
    private User raisedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    private LocalDateTime resolvedAt;
}
