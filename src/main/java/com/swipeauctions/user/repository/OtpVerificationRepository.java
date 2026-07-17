package com.swipeauctions.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.user.entity.OtpVerification;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpVerificationRepository
        extends JpaRepository<OtpVerification, UUID> {

    Optional<OtpVerification> findByEmail(
            String email
    );

    boolean existsByEmail(
            String email
    );

    void deleteByEmail(
            String email
    );
}