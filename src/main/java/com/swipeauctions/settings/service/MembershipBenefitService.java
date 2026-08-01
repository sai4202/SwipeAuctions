package com.swipeauctions.settings.service;

import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.entity.MembershipBenefit;
import com.swipeauctions.settings.repository.MembershipBenefitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-managed membership benefit list (dynamic add/remove + per-tier toggle) shown under every
 * subscription tier card. Kept separate from {@link PlatformSettingsService}, which is scoped to
 * the fixed-row-count registration fee / subscription price settings — benefits have a different,
 * dynamic shape (rows are added and removed, not just edited).
 */
@Service
@RequiredArgsConstructor
public class MembershipBenefitService {

    private final MembershipBenefitRepository benefitRepository;

    @Transactional(readOnly = true)
    public List<MembershipBenefit> listBenefits() {
        return benefitRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public MembershipBenefit addBenefit(String name) {
        MembershipBenefit benefit = MembershipBenefit.builder()
                .name(name)
                .sortOrder((int) benefitRepository.count())
                .enabledTiers(new HashSet<>())
                .build();
        return benefitRepository.save(benefit);
    }

    @Transactional
    public List<MembershipBenefit> updateBenefitTiers(List<TierUpdate> updates) {
        for (TierUpdate update : updates) {
            MembershipBenefit benefit = benefitRepository.findById(update.benefitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Benefit not found: " + update.benefitId()));
            benefit.setEnabledTiers(new HashSet<>(update.enabledTiers()));
            benefitRepository.save(benefit);
        }
        return listBenefits();
    }

    @Transactional
    public void removeBenefit(UUID id) {
        if (!benefitRepository.existsById(id)) {
            throw new ResourceNotFoundException("Benefit not found: " + id);
        }
        benefitRepository.deleteById(id);
    }

    public record TierUpdate(UUID benefitId, Set<SubscriptionTier> enabledTiers) {}
}
