package com.swipeauctions.referral.service;

import com.swipeauctions.referral.entity.Referral;
import com.swipeauctions.referral.enums.ReferralStatus;
import com.swipeauctions.referral.repository.ReferralRepository;
import com.swipeauctions.settings.service.PlatformSettingsService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Turns a tracked referral into a paid one. Deliberately kept out of WalletService, same
 * separation WalletService already uses for other cross-domain crediting (see the withdrawal
 * fulfilment comment on {@code initiateWithdrawal}) — this class owns the referral business rule,
 * WalletService just knows how to move money.
 */
@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final PlatformSettingsService settingsService;
    private final WalletService walletService;

    /**
     * Called right after a real top-up is credited (RazorpayPaymentService, and the dev-only free
     * top-up endpoint). A referral becomes SUCCESSFUL — and its bonus is credited — the first time
     * the referred user's *single* top-up meets the admin-configured minimum deposit; smaller
     * top-ups, even several of them, never trigger it. The row lock on Referral (not Wallet) makes
     * this idempotent against a retried/duplicate call for the same top-up.
     */
    @Transactional
    public void onTopUp(User referredUser, BigDecimal topUpAmount) {
        BigDecimal threshold = settingsService.getReferralMinDeposit();
        if (topUpAmount.compareTo(threshold) < 0) {
            return;
        }

        Referral referral = referralRepository.findByReferred_IdForUpdate(referredUser.getId()).orElse(null);
        if (referral == null || referral.getStatus() == ReferralStatus.SUCCESSFUL) {
            return;
        }

        BigDecimal bonus = settingsService.getReferralBonusAmount();
        referral.setStatus(ReferralStatus.SUCCESSFUL);
        referral.setRewardAmount(bonus);
        referral.setCreditedAt(LocalDateTime.now());
        referralRepository.save(referral);

        if (bonus.signum() > 0) {
            walletService.creditReferralBonus(referral.getReferrer(), bonus, "REFERRAL", referral.getId().toString());
        }
    }
}
