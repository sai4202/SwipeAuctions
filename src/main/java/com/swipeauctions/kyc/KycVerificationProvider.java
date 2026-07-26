package com.swipeauctions.kyc;

import com.swipeauctions.user.dtos.KycSubmitRequestDTO;
import com.swipeauctions.user.entity.KycVerification;

/**
 * Verifies a KYC submission. {@link com.swipeauctions.user.serviceImpl.KycServiceImpl} picks the
 * first enabled non-manual provider, falling back to {@link ManualReviewKycProvider} — so wiring up
 * a real Indian eKYC vendor later is a new class whose {@code isEnabled()} flips on once configured,
 * with no other code changes. Deliberately independent of {@code com.swipeauctions.payment}.
 */
public interface KycVerificationProvider {

    String key();

    boolean isEnabled();

    KycSubmissionResult submit(KycVerification kyc, KycSubmitRequestDTO request);
}
