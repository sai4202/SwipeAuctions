package com.swipeauctions.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Used when a login is blocked by the device-limit cap: the caller re-proves identity with their
 * credentials (they have no JWT yet) and picks which existing session to log out.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutDeviceRequestDTO {

    @NotBlank(message = "Email or mobile number is required")
    private String emailOrMobile;

    @NotBlank(message = "Password is required")
    private String password;

    @NotNull(message = "Session id is required")
    private UUID sessionId;
}
