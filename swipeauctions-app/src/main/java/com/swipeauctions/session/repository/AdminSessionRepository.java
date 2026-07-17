package com.swipeauctions.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.session.entity.AdminSessions;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminSessionRepository extends JpaRepository<AdminSessions, UUID> {

    long countByAdminAndActiveTrue(Admin admin);

    Optional<AdminSessions> findByJwtIdAndActiveTrue(String jwtId);

    Optional<AdminSessions> findByAdminAndActiveTrue(Admin admin);

}