package com.swipeauctions.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.entity.AdminPasswordResetToken;


import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminPasswordResetTokenRepository extends JpaRepository<AdminPasswordResetToken, UUID>
{
    Optional<AdminPasswordResetToken> findByToken(String token);

    Optional<AdminPasswordResetToken> findByAdmin(Admin admin);

}