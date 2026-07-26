package com.swipeauctions.user.repository;

import com.swipeauctions.user.entity.LoginOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoginOtpRepository extends JpaRepository<LoginOtp, UUID> {

    Optional<LoginOtp> findByEmail(String email);
}
