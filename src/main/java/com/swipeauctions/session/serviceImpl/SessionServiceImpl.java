package com.swipeauctions.session.serviceImpl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.session.dtos.SessionResponseDTO;
import com.swipeauctions.session.entity.UserSessions;
import com.swipeauctions.session.repository.UserSessionRepository;
import com.swipeauctions.session.service.SessionService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionServiceImpl implements SessionService {

    private final UserSessionRepository userSessionRepository;

    private final UserRepository userRepository;


    @Override
    public List<SessionResponseDTO> getActiveSessions(String email)
    {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return userSessionRepository
                .findByUserAndActiveTrueOrderByLoginTimeDesc(user)
                .stream()
                .map(session -> SessionResponseDTO.builder()
                        .sessionId(session.getId())
                        .deviceId(session.getDeviceId())
                        .deviceName(session.getDeviceName())
                        .ipAddress(session.getIpAddress())
                        .active(session.getActive())
                        .loginTime(session.getLoginTime())
                        .lastActivityTime(session.getLastActivityTime())
                        .build())
                .toList();
    }

    @Override
    public String logoutSession(UUID sessionId, String email) {

        User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserSessions userSession = userSessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        // Prevent users from logging out sessions
        // that do not belong to them
        if (!userSession.getUser().getId().equals(user.getId()))
        {
            throw new BadRequestException("You cannot logout another user's session");
        }

        // Prevent duplicate logout
        if (!Boolean.TRUE.equals(userSession.getActive()))
        {
            throw new BadRequestException("Session is already logged out");
        }

        userSession.setActive(false);

        userSession.setLogoutTime(LocalDateTime.now());

        userSessionRepository.save(userSession);

        return "Session logged out successfully";
    }

    @Override
    public String logoutAllSessions(String email)
    {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<UserSessions> activeSessions = userSessionRepository.findByUserAndActiveTrue(user);

        if (activeSessions.isEmpty())
        {
            throw new BadRequestException("No active sessions found");
        }

        //every session get the same logout time
        LocalDateTime logoutTime = LocalDateTime.now();

        activeSessions.forEach(session ->
        {
            session.setActive(false);

            session.setLogoutTime(logoutTime);
        });

        userSessionRepository.saveAll(activeSessions);

        return "Logged out from all devices successfully";
    }
}