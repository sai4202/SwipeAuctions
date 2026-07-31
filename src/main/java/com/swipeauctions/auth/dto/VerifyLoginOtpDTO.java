package com.swipeauctions.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyLoginOtpDTO {

    @NotBlank(message = "Email or Mobile Number is required")
    private String emailOrMobile;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String otp;

    /** Optional exact device model (e.g. "Pixel 9") from the User-Agent Client Hints API — only
     *  Chromium browsers on Android can supply this; null everywhere else (iOS, Firefox, desktop). */
    private String clientDeviceModel;
}
