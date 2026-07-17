package com.swipeauctions.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.swipeauctions.common.response.ApiResponse;
import com.swipeauctions.user.dtos.ChangeEmailRequestDTO;
import com.swipeauctions.user.dtos.ChangeMobileRequestDTO;
import com.swipeauctions.user.dtos.VerifyEmailChangeDTO;
import com.swipeauctions.user.dtos.VerifyMobileChangeDTO;
import com.swipeauctions.user.service.UserService;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/change-email-request")
    public ResponseEntity<ApiResponse<String>> changeEmailRequest(

            @Valid
            @RequestBody
            ChangeEmailRequestDTO request,

            Principal principal
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.requestEmailChange(
                                request,
                                principal.getName()
                        ),
                        null
                )
        );
    }

    @PostMapping("/verify-email-change")
    public ResponseEntity<ApiResponse<String>> verifyEmailChange(

            @Valid
            @RequestBody
            VerifyEmailChangeDTO request,

            Principal principal
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.verifyEmailChange(
                                request,
                                principal.getName()
                        ),
                        null
                )
        );
    }

    @PostMapping("/email/resend-otp")
    public ApiResponse<String> resendEmailOtp(
            Authentication authentication)
    {
        return ApiResponse.success(userService.resendEmailChangeOtp(
                        authentication.getName()),null);
    }

    @PostMapping("/change-mobile-request")
    public ResponseEntity<ApiResponse<String>> changeMobileRequest(

            @Valid
            @RequestBody
            ChangeMobileRequestDTO request,

            Principal principal
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.requestMobileChange(
                                request,
                                principal.getName()
                        ),
                        null
                )
        );
    }

    @PostMapping("/verify-mobile-change")
    public ResponseEntity<ApiResponse<String>> verifyMobileChange(

            @Valid
            @RequestBody
            VerifyMobileChangeDTO request,

            Principal principal
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.verifyMobileChange(
                                request,
                                principal.getName()
                        ),
                        null
                )
        );
    }

    @PostMapping("/mobile/resend-otp")
    public ApiResponse<String> resendMobileOtp(Authentication authentication)
    {
        return ApiResponse.success(userService
                .resendMobileChangeOtp(authentication.getName()),null);
    }
}