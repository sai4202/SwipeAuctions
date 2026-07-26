package com.swipeauctions.auth.dto;

import lombok.*;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.session.dtos.SessionResponseDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDTO {

    private UUID userId;

    private String email;

    private String mobileNumber;

    private String token;

    private String tokenType;

    private String role;

    private Boolean active;

    private Boolean emailVerified;

    private Boolean mobileVerified;

    private Boolean kycCompleted;

    private SubscriptionTier subscriptionTier;

    private LocalDateTime subscriptionExpiresAt;

    private Boolean deviceLimitReached;

    private String message;

    private List<SessionResponseDTO> activeSessions;
}