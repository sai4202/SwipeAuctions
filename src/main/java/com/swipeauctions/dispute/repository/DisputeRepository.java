package com.swipeauctions.dispute.repository;

import com.swipeauctions.dispute.entity.Dispute;
import com.swipeauctions.dispute.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    List<Dispute> findByStatus(DisputeStatus status);

    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);

    List<Dispute> findByAuction_Id(UUID auctionId);

    List<Dispute> findByRaisedBy_Id(UUID userId);

    boolean existsByAuction_IdAndStatusIn(UUID auctionId, Collection<DisputeStatus> statuses);
}
