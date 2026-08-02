package com.swipeauctions.admin.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/**
 * Used when an admin login is blocked by the single-session cap: the caller re-proves identity with
 * their credentials (they have no JWT yet) and confirms logging out the existing session.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminLogoutDeviceRequestDTO {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotNull(message = "Session id is required")
    private UUID sessionId;
}
