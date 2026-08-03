package com.swipeauctions.referral.repository;

import com.swipeauctions.referral.entity.Referral;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    boolean existsByReferred_Id(UUID referredId);

    List<Referral> findByReferrer_Id(UUID referrerId);

    List<Referral> findAllByOrderByCreatedAtDesc();

    /** Row-locked lookup used by ReferralService#onTopUp so a retried/duplicate top-up call can't
     *  credit the same referral's bonus twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.referred.id = :referredId")
    Optional<Referral> findByReferred_IdForUpdate(@Param("referredId") UUID referredId);
}
