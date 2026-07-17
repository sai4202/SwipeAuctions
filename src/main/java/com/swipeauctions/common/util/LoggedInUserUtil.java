package com.swipeauctions.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.common.exception.UnauthorizedException;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class LoggedInUserUtil {

    private final UserRepository userRepository;

    private final AdminRepository adminRepository;

    //Returns currently authenticated user.

    public User getCurrentUser()
    {
        String email = getCurrentUserEmail();

        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Logged in user not found"));
    }

    /**
     * Returns currently authenticated admin.
     */
    public Admin getCurrentAdmin()
    {
        String email = getCurrentUserEmail();

        return adminRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Logged in admin not found"));
    }

    /**
     * Returns email of currently authenticated user.
     */
    public String getCurrentUserEmail() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal()))
        {
            throw new UnauthorizedException("No authenticated user found");
        }

        return authentication.getName();
    }
}