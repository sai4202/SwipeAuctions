package com.swipeauctions.admin.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.admin.service.AdminLoginSecurityService;
import com.swipeauctions.common.exception.UnauthorizedException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminLoginValidationService {

    private final AdminRepository adminRepository;

    private final AdminLoginSecurityService adminSecurityService;

    // Validates admin account status before login.
    public void validateAdminAccountStatus(Admin admin)
    {

        // Reject inactive admin accounts.
        if (!Boolean.TRUE.equals(admin.getActive()))
        {
            throw new UnauthorizedException("Admin account is inactive");
        }

        // Validate account lock status.
        if (!Boolean.TRUE.equals(admin.getAccountNonLocked()))
        {

            // Reject login if lock duration is active.
            if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(LocalDateTime.now()))
            {
                throw new UnauthorizedException("Account is locked until " + admin.getLockedUntil());
            }

            // Unlock account after lock duration expires.
            admin.setAccountNonLocked(true);
            admin.setFailedLoginAttempts(0);
            admin.setLockedUntil(null);

            adminRepository.save(admin);
        }
    }

    // Handles failed login attempts.
    public void handleFailedLogin(Admin admin) {

        int attempts = admin.getFailedLoginAttempts() == null ? 1 : admin.getFailedLoginAttempts() + 1;

        adminSecurityService.updateFailedLoginAttempt(admin, attempts);

        if (attempts >= 5)
        {
            throw new UnauthorizedException("Account locked due to multiple failed login attempts. Please Try again after 1 minute");
        }

        int remainingAttempts = 5 - attempts;

        throw new UnauthorizedException("Invalid credentials. " + remainingAttempts + " login attempt(s) remaining.");
    }
}
