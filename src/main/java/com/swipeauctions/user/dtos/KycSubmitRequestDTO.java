package com.swipeauctions.user.dtos;

import com.swipeauctions.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycSubmitRequestDTO {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private LocalDate dateOfBirth;

    private Gender gender;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Pincode is required")
    private String pincode;

    @NotBlank(message = "Aadhaar number is required")
    private String aadhaarNumber;

    @NotBlank(message = "PAN number is required")
    private String panNumber;
}
