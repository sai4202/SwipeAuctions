package com.swipeauctions.admin.dtos;

import com.swipeauctions.session.dtos.SessionResponseDTO;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminLoginResponseDTO {

    private UUID adminId;

    private String email;

    private String token;

    private String tokenType;

    private String role;

    private String adminRole;

    private Boolean active;

    // Set instead of throwing when the single-session cap is already held by another device — lets
    // the login screen show that device and offer to log it out, same UX as the user login flow.
    private Boolean deviceLimitReached;

    private String message;

    private List<SessionResponseDTO> activeSessions;
}