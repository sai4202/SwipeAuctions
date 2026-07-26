package com.swipeauctions.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.user.entity.KycVerification;
import com.swipeauctions.user.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycVerificationRepository
        extends JpaRepository<KycVerification, UUID> {

    Optional<KycVerification> findByUser(
            User user
    );

    Optional<KycVerification> findByUser_Id(UUID userId);

    List<KycVerification> findByStatus(KycStatus status);

    Page<KycVerification> findByStatus(KycStatus status, Pageable pageable);

}