package com.swipeauctions.referral.repository;

import com.swipeauctions.referral.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    boolean existsByReferred_Id(UUID referredId);

    List<Referral> findByReferrer_Id(UUID referrerId);

    List<Referral> findAllByOrderByCreatedAtDesc();
}
