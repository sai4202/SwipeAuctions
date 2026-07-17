package com.swipeauctions.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequestDTO {

    @NotBlank(message = "Email or Mobile Number is required")
    private String emailOrMobile;

    @NotBlank(message = "Password is required")
    private String password;
}