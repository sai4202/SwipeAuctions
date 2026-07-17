package com.swipeauctions.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.swipeauctions.user.entity.KycVerification;
import com.swipeauctions.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface KycVerificationRepository
        extends JpaRepository<KycVerification, UUID> {

    Optional<KycVerification> findByUser(
            User user
    );

}