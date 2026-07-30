package com.swipeauctions.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import com.swipeauctions.auth.dto.*;
import com.swipeauctions.user.dtos.RegisterRequestDTO;

public interface UserAuthService {

    String register(
            RegisterRequestDTO request
    );

    String verifyMobileOtp(
            VerifyMobileOtpDTO request
    );

    String verifyEmailOtp(
            VerifyEmailOtpDTO request
    );
    LoginResponseDTO login(
            LoginRequestDTO request,
            HttpServletRequest httpServletRequest
    );

    /** Sends a one-shot login code to the account's email (same delivery channel as "mobile" OTPs elsewhere in this codebase — no real SMS gateway exists). */
    String requestLoginOtp(
            RequestLoginOtpDTO request
    );

    /** Verifies a login OTP and, on success, issues a session identically to password login. */
    LoginResponseDTO verifyLoginOtp(
            VerifyLoginOtpDTO request,
            HttpServletRequest httpServletRequest
    );
    String forgotPassword(
            ForgotPasswordRequestDTO request
    );

    String resetPassword(
            ResetPasswordRequestDTO request
    );

    String logout(
            String token
    );

    String changePassword(
            ChangePasswordRequestDTO request,
            String email
    );

    String resendOtp(
            ResendOtpDTO request
    );

    /**
     * Called from the login screen when {@link LoginResponseDTO#getDeviceLimitReached()} was true:
     * re-verifies the caller's credentials (no JWT exists yet) and logs out the chosen session so a
     * retried login can succeed.
     */
    String logoutDevice(
            LogoutDeviceRequestDTO request
    );

    // Registration fee payment moved to RazorpayPaymentService — real Razorpay payment, not a
    // free stub. See UserAuthController's /registration-fee/order and /registration-fee/verify.
}