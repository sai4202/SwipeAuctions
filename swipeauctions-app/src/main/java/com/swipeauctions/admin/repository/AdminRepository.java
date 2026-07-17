package com.swipeauctions.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.admin.entity.Admin;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByEmail(String email);

    Optional<Admin> findByMobileNumber(String mobileNumber);

    boolean existsByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);
}