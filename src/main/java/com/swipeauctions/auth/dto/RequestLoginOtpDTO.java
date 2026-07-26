package com.swipeauctions.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestLoginOtpDTO {

    @NotBlank(message = "Email or Mobile Number is required")
    private String emailOrMobile;
}
