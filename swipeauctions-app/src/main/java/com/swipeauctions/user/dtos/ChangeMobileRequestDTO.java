package com.swipeauctions.user.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeMobileRequestDTO {

    @NotBlank(message = "Mobile Number is required")
    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Invalid Mobile Number"
    )
    private String newMobileNumber;
}