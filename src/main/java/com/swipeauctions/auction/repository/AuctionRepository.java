package com.swipeauctions.auction.repository;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.auction.enums.AuctionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    List<Auction> findByStatus(AuctionStatus status);

    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);

    /** Auctions with at least one bid — the "Active Biddings" admin view. Filtering at the DB level
     *  (rather than the per-row bidCount computed after fetch in AdminController#toAuction) keeps
     *  pagination correct — a page can't come back short just because some fetched rows had none. */
    @Query("select a from Auction a where a.status = :status and exists (select 1 from Bid b where b.auction = a)")
    Page<Auction> findByStatusAndHasBids(@Param("status") AuctionStatus status, Pageable pageable);

    Optional<Auction> findByListing_Id(UUID listingId);

    List<Auction> findByStatusAndStartTimeLessThanEqual(AuctionStatus status, LocalDateTime time);

    List<Auction> findByStatusAndCurrentEndTimeLessThanEqual(AuctionStatus status, LocalDateTime time);

    List<Auction> findByEvent_Id(UUID eventId);

    long countByEvent_Id(UUID eventId);

    List<Auction> findByCurrentWinner_IdAndStatusOrderByCurrentEndTimeDesc(UUID winnerId, AuctionStatus status);

    /** Pessimistic row lock used by BidService so concurrent bids on one auction serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") UUID id);
}
