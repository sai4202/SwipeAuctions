package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.SaleProceedsHold;
import com.swipeauctions.wallet.enums.ProceedsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleProceedsHoldRepository extends JpaRepository<SaleProceedsHold, UUID> {

    Optional<SaleProceedsHold> findByAuction_Id(UUID auctionId);

    List<SaleProceedsHold> findByStatusAndCreatedAtBefore(ProceedsStatus status, LocalDateTime threshold);
}
