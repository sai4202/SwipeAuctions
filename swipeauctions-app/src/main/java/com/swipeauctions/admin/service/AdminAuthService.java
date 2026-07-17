package com.swipeauctions.admin.service;

import jakarta.servlet.http.HttpServletRequest;
import com.swipeauctions.admin.dtos.*;
import com.swipeauctions.auth.dto.ChangePasswordRequestDTO;
import com.swipeauctions.auth.dto.ForgotPasswordRequestDTO;
import com.swipeauctions.auth.dto.ResetPasswordRequestDTO;

public interface AdminAuthService {

    String register(AdminRegisterRequestDTO request);

    AdminLoginResponseDTO login(AdminLoginRequestDTO request, HttpServletRequest httpServletRequest);

    String forgotPassword(ForgotPasswordRequestDTO request);

    String resetPassword(ResetPasswordRequestDTO request);

    String changePassword(ChangePasswordRequestDTO request);

    String logout();
}