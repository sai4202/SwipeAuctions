package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.SaleProceedsHold;
import com.swipeauctions.wallet.enums.ProceedsStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleProceedsHoldRepository extends JpaRepository<SaleProceedsHold, UUID> {

    Optional<SaleProceedsHold> findByAuction_Id(UUID auctionId);

    List<SaleProceedsHold> findByStatusAndCreatedAtBefore(ProceedsStatus status, LocalDateTime threshold);

    /**
     * Pessimistic row lock for release/refund — same double-release race as {@link
     * com.swipeauctions.wallet.repository.BidEligibilityHoldRepository}'s locking finder (see
     * Findings_pendings.md #3): the auto-release scheduler and an admin's dispute resolution can
     * both act on the same ACTIVE hold concurrently otherwise.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SaleProceedsHold h where h.id = :id")
    Optional<SaleProceedsHold> findByIdForUpdate(@Param("id") UUID id);
}
