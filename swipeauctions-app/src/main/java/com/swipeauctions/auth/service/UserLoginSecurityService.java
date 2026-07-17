package com.swipeauctions.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserLoginSecurityService {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailedLoginAttempt(
            User user,
            int attempts
    ) {

        user.setFailedLoginAttempts(attempts);

        if (attempts >= 5) {

            user.setAccountNonLocked(false);

            user.setLockedUntil(
                    LocalDateTime.now().plusMinutes(1)
            );
        }

        userRepository.save(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetFailedLoginAttempts(User user)
    {

        if (user.getFailedLoginAttempts() == null || user.getFailedLoginAttempts() == 0)
        {
            return;
        }

        user.setFailedLoginAttempts(0);

        user.setAccountNonLocked(true);

        user.setLockedUntil(null);

        userRepository.save(user);
    }
}

