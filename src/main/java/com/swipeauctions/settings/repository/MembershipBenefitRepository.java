package com.swipeauctions.settings.repository;

import com.swipeauctions.settings.entity.MembershipBenefit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MembershipBenefitRepository extends JpaRepository<MembershipBenefit, UUID> {
    List<MembershipBenefit> findAllByOrderBySortOrderAsc();
}
