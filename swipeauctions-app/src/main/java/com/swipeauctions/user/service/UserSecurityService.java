package com.swipeauctions.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.session.entity.UserSessions;
import com.swipeauctions.session.repository.UserSessionRepository;
import com.swipeauctions.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSecurityService {

    private final UserSessionRepository userSessionRepository;

    public void invalidateUserSessions(User user)
    {
        List<UserSessions> activeSessions =
                userSessionRepository
                        .findByUserAndActiveTrue(user);

        activeSessions.forEach(session -> {

            session.setActive(false);

            session.setLogoutTime(LocalDateTime.now()
            );
        });

        userSessionRepository.saveAll(activeSessions);
    }
}