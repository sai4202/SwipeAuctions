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

    /** Optional exact device model (e.g. "Pixel 9") from the User-Agent Client Hints API — only
     *  Chromium browsers on Android can supply this; null everywhere else (iOS, Firefox, desktop). */
    private String clientDeviceModel;
}