package com.swipeauctions.session.service;

import com.swipeauctions.session.dtos.SessionResponseDTO;

import java.util.List;
import java.util.UUID;

public interface SessionService {

    List<SessionResponseDTO> getActiveSessions(String email);

    String logoutSession(UUID sessionId, String email);

    String logoutAllSessions(String email);
}