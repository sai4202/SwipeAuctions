package com.swipeauctions.auth.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.auth.config.MobileVerificationConfig;
import com.swipeauctions.auth.service.UserLoginSecurityService;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.UnauthorizedException;
import com.swipeauctions.user.entity.User;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserLoginValidationService {

    private final UserLoginSecurityService userLoginSecurityService;

    private final MobileVerificationConfig mobileVerificationConfig;

    public void validateVerificationStatus(User user)
    {
        if (!Boolean.TRUE.equals(user.getEmailVerified()))
        {
            throw new BadRequestException("Email verification pending");
        }

        if (mobileVerificationConfig.isRequired() && !Boolean.TRUE.equals(user.getMobileVerified()))
        {
            throw new BadRequestException("Mobile verification pending");
        }
    }

    public void validateUserAccountStatus(User user)
    {
        if (!Boolean.TRUE.equals(user.getActive()))
        {
            throw new UnauthorizedException("Account is not active");
        }

        if (!Boolean.TRUE.equals(user.getAccountNonLocked()))
        {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
            {
                throw new UnauthorizedException("Account locked due to multiple failed login attempts. " + "Try again later.");
            }

            userLoginSecurityService.resetFailedLoginAttempts(user);
        }
    }

    public void handleFailedLogin(User user)
    {
        int attempts = user.getFailedLoginAttempts() + 1;

        userLoginSecurityService.updateFailedLoginAttempt(user, attempts);

        int remainingAttempts = 5 - attempts;

        if (remainingAttempts <= 0)
        {
            throw new UnauthorizedException("Account locked due to multiple failed login attempts. " + "Please try again after 1 minute.");
        }

        throw new UnauthorizedException("Invalid credentials. " + remainingAttempts + " login attempt(s) remaining.");
    }

}