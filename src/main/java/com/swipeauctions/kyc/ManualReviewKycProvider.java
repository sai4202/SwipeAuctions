package com.swipeauctions.kyc;

import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.user.dtos.KycSubmitRequestDTO;
import com.swipeauctions.user.entity.KycVerification;
import org.springframework.stereotype.Component;

/** Always-enabled fallback: every submission lands in a real PENDING state for an admin to review. */
@Component
public class ManualReviewKycProvider implements KycVerificationProvider {

    public static final String KEY = "MANUAL";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public KycSubmissionResult submit(KycVerification kyc, KycSubmitRequestDTO request) {
        return new KycSubmissionResult(null, KycStatus.PENDING, null);
    }
}
