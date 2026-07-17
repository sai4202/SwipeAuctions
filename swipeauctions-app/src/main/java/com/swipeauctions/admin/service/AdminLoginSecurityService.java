package com.swipeauctions.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.repository.AdminRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminLoginSecurityService {

    private final AdminRepository adminRepository;

    /**
     * Updates failed login attempts in a separate transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailedLoginAttempt(Admin admin, int attempts)
    {

        admin.setFailedLoginAttempts(attempts);

        // Lock account after maximum attempts.
        if (attempts >= 5) {

            admin.setAccountNonLocked(false);

            admin.setLockedUntil(LocalDateTime.now().plusMinutes(1));
        }

        adminRepository.save(admin);
    }

    /**
     * Resets failed login attempts after successful authentication.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetFailedLoginAttempts(Admin admin)
    {

        if (admin.getFailedLoginAttempts() == null || admin.getFailedLoginAttempts() == 0)
        {
            return;
        }

        admin.setFailedLoginAttempts(0);

        admin.setAccountNonLocked(true);

        admin.setLockedUntil(null);

        adminRepository.save(admin);
    }
}