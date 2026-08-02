package com.swipeauctions.admin.dtos;

import lombok.*;

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
}