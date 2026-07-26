package com.swipeauctions.user.serviceImpl;

import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.kyc.KycSubmissionResult;
import com.swipeauctions.kyc.KycVerificationProvider;
import com.swipeauctions.kyc.ManualReviewKycProvider;
import com.swipeauctions.user.dtos.KycStatusResponseDTO;
import com.swipeauctions.user.dtos.KycSubmitRequestDTO;
import com.swipeauctions.user.entity.KycVerification;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.KycVerificationRepository;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.user.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Submissions go to the active {@link KycVerificationProvider} — today that's always
 * {@link ManualReviewKycProvider} (real PENDING state, reviewed by an admin below), since no real
 * Indian eKYC vendor is wired up yet (see TASKS.md Phase 3). Adding one later just means a new
 * provider whose {@code isEnabled()} flips on once configured — no changes needed here.
 */
@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {

    private final UserRepository userRepository;

    private final KycVerificationRepository kycVerificationRepository;

    private final List<KycVerificationProvider> providers;

    @Override
    @Transactional
    public KycStatusResponseDTO submitKyc(KycSubmitRequestDTO request, String email)
    {
        User user = getUser(email);

        KycVerification kyc = kycVerificationRepository.findByUser(user)
                .orElseGet(() -> KycVerification.builder().user(user).build());

        kyc.setFullName(request.getFullName());
        kyc.setDateOfBirth(request.getDateOfBirth());
        kyc.setGender(request.getGender());
        kyc.setAddress(request.getAddress());
        kyc.setCity(request.getCity());
        kyc.setState(request.getState());
        kyc.setPincode(request.getPincode());
        kyc.setAadhaarMasked(maskKeepingLast(request.getAadhaarNumber(), 4));
        kyc.setPanNumberMasked(maskKeepingLast(request.getPanNumber(), 4));
        kyc.setSubmittedAt(LocalDateTime.now());
        kyc.setVerifiedAt(null);
        kyc.setReviewedBy(null);

        KycVerificationProvider provider = activeProvider();
        KycSubmissionResult result = provider.submit(kyc, request);

        kyc.setProvider(provider.key());
        kyc.setProviderReferenceId(result.providerReferenceId());
        kyc.setStatus(result.status());
        kyc.setRemarks(result.remarks());

        kycVerificationRepository.save(kyc);

        user.setKycCompleted(result.status() == KycStatus.APPROVED);
        user.setKycStatus(result.status());
        userRepository.save(user);

        return toResponse(kyc, user);
    }

    @Override
    public KycStatusResponseDTO getStatus(String email)
    {
        User user = getUser(email);

        return kycVerificationRepository.findByUser(user)
                .map(kyc -> toResponse(kyc, user))
                .orElseGet(() -> KycStatusResponseDTO.builder()
                        .kycCompleted(false)
                        .status(KycStatus.NOT_SUBMITTED)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<KycVerification> listForAdmin(KycStatus statusFilter, Pageable pageable)
    {
        return statusFilter != null
                ? kycVerificationRepository.findByStatus(statusFilter, pageable)
                : kycVerificationRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public KycVerification getForAdmin(UUID userId)
    {
        return kycVerificationRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No KYC submission for this user"));
    }

    @Override
    @Transactional
    public KycVerification approve(UUID userId, String remarks, String reviewedBy)
    {
        KycVerification kyc = getForAdmin(userId);
        requirePending(kyc);

        kyc.setStatus(KycStatus.APPROVED);
        kyc.setVerifiedAt(LocalDateTime.now());
        kyc.setRemarks(remarks);
        kyc.setReviewedBy(reviewedBy);
        kycVerificationRepository.save(kyc);

        User user = kyc.getUser();
        user.setKycCompleted(true);
        user.setKycStatus(KycStatus.APPROVED);
        userRepository.save(user);

        return kyc;
    }

    @Override
    @Transactional
    public KycVerification reject(UUID userId, String remarks, String reviewedBy)
    {
        KycVerification kyc = getForAdmin(userId);
        requirePending(kyc);

        kyc.setStatus(KycStatus.REJECTED);
        kyc.setVerifiedAt(LocalDateTime.now());
        kyc.setRemarks(remarks);
        kyc.setReviewedBy(reviewedBy);
        kycVerificationRepository.save(kyc);

        User user = kyc.getUser();
        user.setKycCompleted(false);
        user.setKycStatus(KycStatus.REJECTED);
        userRepository.save(user);

        return kyc;
    }

    private void requirePending(KycVerification kyc)
    {
        if (kyc.getStatus() != KycStatus.PENDING)
        {
            throw new BadRequestException("Only a PENDING KYC submission can be reviewed (current status: "
                    + kyc.getStatus() + ")");
        }
    }

    /** Prefers a real, enabled provider over the always-on manual fallback. */
    private KycVerificationProvider activeProvider()
    {
        return providers.stream()
                .filter(p -> !ManualReviewKycProvider.KEY.equals(p.key()))
                .filter(KycVerificationProvider::isEnabled)
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(p -> ManualReviewKycProvider.KEY.equals(p.key()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No KYC provider configured, not even manual")));
    }

    private User getUser(String email)
    {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private KycStatusResponseDTO toResponse(KycVerification kyc, User user)
    {
        return KycStatusResponseDTO.builder()
                .kycCompleted(user.getKycCompleted())
                .status(kyc.getStatus())
                .fullName(kyc.getFullName())
                .submittedAt(kyc.getSubmittedAt())
                .verifiedAt(kyc.getVerifiedAt())
                .remarks(kyc.getRemarks())
                .build();
    }

    /** Masks all but the last {@code keepLast} characters, e.g. Aadhaar/PAN — never store raw ID numbers. */
    private String maskKeepingLast(String value, int keepLast)
    {
        String cleaned = value.trim().replaceAll("\\s+", "").toUpperCase();

        if (cleaned.length() <= keepLast)
        {
            return cleaned;
        }

        return "X".repeat(cleaned.length() - keepLast) + cleaned.substring(cleaned.length() - keepLast);
    }
}
