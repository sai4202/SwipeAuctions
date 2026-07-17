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
}