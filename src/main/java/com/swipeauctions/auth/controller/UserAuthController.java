package com.swipeauctions.auth.controller;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.swipeauctions.auth.dto.*;
import com.swipeauctions.auth.service.UserAuthService;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.response.ApiResponse;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.payment.RazorpayPaymentService;
import com.swipeauctions.user.dtos.RegisterRequestDTO;
import com.swipeauctions.user.entity.User;
import jakarta.validation.constraints.NotBlank;

import java.security.Principal;

// Handles authentication related APIs
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserAuthService userAuthService;
    private final RazorpayPaymentService razorpayPaymentService;
    private final LoggedInUserUtil loggedInUserUtil;

    public record VerifyPaymentDTO(
            @NotBlank String orderId, @NotBlank String paymentId, @NotBlank String signature) {}

    // Register a new user
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(
            @Valid
            @RequestBody RegisterRequestDTO request
    ) {

        return ResponseEntity.ok(ApiResponse.success(userAuthService.register(request),null));
    }

    // Verify email OTP
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmailOtp(
            @Valid
            @RequestBody VerifyEmailOtpDTO request
    ) {

        return ResponseEntity.ok(ApiResponse.success(userAuthService.verifyEmailOtp(request),null));
    }

    // Verify mobile OTP
    @PostMapping("/verify-mobile")
    public ResponseEntity<ApiResponse<String>> verifyMobileOtp(
            @Valid
            @RequestBody VerifyMobileOtpDTO request
    ) {

        return ResponseEntity.ok(ApiResponse.success(userAuthService.verifyMobileOtp(request),null));
    }

    // Authenticate user and generate JWT
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(

            @Valid
            @RequestBody LoginRequestDTO request,
            HttpServletRequest httpServletRequest
    ) {
        LoginResponseDTO response = userAuthService.login(request, httpServletRequest);

        String message = Boolean.TRUE.equals(response.getDeviceLimitReached()) ? response.getMessage() : "Login successful";

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    // Request a login OTP, delivered to the account's email.
    @PostMapping("/login/otp/request")
    public ResponseEntity<ApiResponse<String>> requestLoginOtp(
            @Valid
            @RequestBody RequestLoginOtpDTO request
    ) {
        return ResponseEntity.ok(ApiResponse.success(userAuthService.requestLoginOtp(request), null));
    }

    // Verify a login OTP and, on success, get a JWT — same response shape as password login.
    @PostMapping("/login/otp/verify")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> verifyLoginOtp(
            @Valid
            @RequestBody VerifyLoginOtpDTO request,
            HttpServletRequest httpServletRequest
    ) {
        LoginResponseDTO response = userAuthService.verifyLoginOtp(request, httpServletRequest);

        String message = Boolean.TRUE.equals(response.getDeviceLimitReached()) ? response.getMessage() : "Login successful";

        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    // Send password reset link
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(

            @Valid
            @RequestBody ForgotPasswordRequestDTO request

    ) {
        return ResponseEntity.ok(ApiResponse.success(userAuthService.forgotPassword(request), null));
    }

    // Reset password using token
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(

            @Valid
            @RequestBody ResetPasswordRequestDTO request

    ) {

        return ResponseEntity.ok(ApiResponse.success(userAuthService.resetPassword(request), null));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(

            @Valid
            @RequestBody
            ChangePasswordRequestDTO request,
            Principal principal

    ) {

        return ResponseEntity.ok(ApiResponse.success(userAuthService.changePassword(request, principal.getName()), null));
    }

    // Final step of registration: real Razorpay payment of the one-time platform registration fee.
    // The frontend logs the user in right after mobile-OTP verification so these calls have a JWT
    // — see RegisterPage's post-OTP flow. Order then verify, same pattern as wallet top-up.
    @PostMapping("/registration-fee/order")
    public ResponseEntity<ApiResponse<RazorpayPaymentService.OrderIntent>> createRegistrationFeeOrder() {
        User user = loggedInUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(null, razorpayPaymentService.createRegistrationFeeOrder(user)));
    }

    @PostMapping("/registration-fee/verify")
    public ResponseEntity<ApiResponse<String>> verifyRegistrationFee(@Valid @RequestBody VerifyPaymentDTO request) {
        User user = loggedInUserUtil.getCurrentUser();
        razorpayPaymentService.verifyAndSettle(user, request.orderId(), request.paymentId(), request.signature());
        return ResponseEntity.ok(ApiResponse.success("Registration fee paid. Your account is now fully active.", null));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<String>> resendOtp(

            @Valid
            @RequestBody ResendOtpDTO request

    ) {

        return ResponseEntity.ok(ApiResponse.success(userAuthService.resendOtp(request), null));
    }

    // Log out a specific device chosen from the "device limit reached" login response, then the
    // frontend retries the login. Public: the caller has no JWT yet, so credentials are re-checked.
    @PostMapping("/logout-device")
    public ResponseEntity<ApiResponse<String>> logoutDevice(
            @Valid
            @RequestBody LogoutDeviceRequestDTO request
    ) {

        return ResponseEntity.ok(ApiResponse.success(userAuthService.logoutDevice(request), null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest request)
    {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {

            throw new BadRequestException("Authorization token is missing");
        }

        String token = authHeader.substring(7);

        String response = userAuthService.logout(token);

        return ResponseEntity.ok(ApiResponse.success(response, null));
    }
}