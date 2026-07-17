package com.swipeauctions.user.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeEmailRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid Email Format")
    private String newEmail;
}