package com.swipeauctions.auth.helper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.swipeauctions.common.util.DeviceUtils;
import com.swipeauctions.session.entity.UserSessions;
import com.swipeauctions.session.repository.UserSessionRepository;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserSessionManagementService {

    private final UserSessionRepository sessionRepository;

    private final UserRepository userRepository;

    public void createUserSession(User user, String jwtId, HttpServletRequest request)
    {

        UserSessions session = new UserSessions();

        session.setUser(user);

        session.setJwtId(jwtId);

        session.setDeviceId(UUID.randomUUID().toString());

        String userAgent = request.getHeader("User-Agent");

        session.setUserAgent(userAgent);

        session.setIpAddress(request.getRemoteAddr());

        session.setDeviceName(DeviceUtils.extractDeviceName(userAgent));

        session.setActive(true);

        LocalDateTime now = LocalDateTime.now();

        session.setLoginTime(now);

        session.setLastActivityTime(now);

        sessionRepository.save(session);
    }


    public void updateUserLoginAudit(User user, HttpServletRequest request)
    {

        user.setLastLoginAt(LocalDateTime.now());

        user.setLastLoginIp(request.getRemoteAddr());

        String userAgent = request.getHeader("User-Agent");

        if (userAgent == null)
        {
            userAgent = "Unknown Device";
        }

        user.setLastLoginDevice(DeviceUtils.extractDeviceName(userAgent));

        userRepository.save(user);
    }

    public void invalidateUserSessions(User user)
    {
        List<UserSessions> activeSessions = sessionRepository.findByUserAndActiveTrue(user);

        activeSessions.forEach(session ->
        {
            session.setActive(false);

            session.setLogoutTime(LocalDateTime.now());
        });

        sessionRepository.saveAll(activeSessions);
    }

    public List<UserSessions> getActiveSessions(User user)
    {
        return sessionRepository.findByUserAndActiveTrue(user);
    }
}