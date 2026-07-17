package com.swipeauctions.session.dtos;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionResponseDTO {

    private UUID sessionId;

    private String deviceId;

    private String deviceName;

    private String ipAddress;

    private Boolean active;

    private LocalDateTime loginTime;

    private LocalDateTime lastActivityTime;
}
