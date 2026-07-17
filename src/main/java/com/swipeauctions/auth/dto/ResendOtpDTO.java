package com.swipeauctions.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendOtpDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid Email Format")
    private String email;
}