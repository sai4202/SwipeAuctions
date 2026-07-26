package com.swipeauctions.user.service;

import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.user.dtos.KycStatusResponseDTO;
import com.swipeauctions.user.dtos.KycSubmitRequestDTO;
import com.swipeauctions.user.entity.KycVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface KycService {

    KycStatusResponseDTO submitKyc(KycSubmitRequestDTO request, String email);

    KycStatusResponseDTO getStatus(String email);

    // ---- Admin review ----

    Page<KycVerification> listForAdmin(KycStatus statusFilter, Pageable pageable);

    KycVerification getForAdmin(UUID userId);

    KycVerification approve(UUID userId, String remarks, String reviewedBy);

    KycVerification reject(UUID userId, String remarks, String reviewedBy);
}
