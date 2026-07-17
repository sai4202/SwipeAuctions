package com.swipeauctions.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.swipeauctions.admin.dtos.AdminLoginRequestDTO;
import com.swipeauctions.admin.dtos.AdminLoginResponseDTO;
import com.swipeauctions.admin.dtos.AdminRegisterRequestDTO;
import com.swipeauctions.admin.service.AdminAuthService;
import com.swipeauctions.auth.dto.ChangePasswordRequestDTO;
import com.swipeauctions.auth.dto.ForgotPasswordRequestDTO;
import com.swipeauctions.auth.dto.ResetPasswordRequestDTO;
import com.swipeauctions.common.response.ApiResponse;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(

            @Valid
            @RequestBody AdminRegisterRequestDTO request
    ) {

        String response = adminAuthService.register(request);

        return ResponseEntity.ok(ApiResponse.success(response, null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponseDTO>> login(

            @Valid
            @RequestBody AdminLoginRequestDTO request,

            HttpServletRequest httpServletRequest
    ) {

        AdminLoginResponseDTO response = adminAuthService.login(request, httpServletRequest);

        return ResponseEntity.ok(ApiResponse.success("Admin login successful", response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(

            @Valid
            @RequestBody ForgotPasswordRequestDTO request

    ) {

        String response = adminAuthService.forgotPassword(request);

        return ResponseEntity.ok(ApiResponse.success(response, null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(

            @Valid
            @RequestBody ResetPasswordRequestDTO request

    ) {

        String response = adminAuthService.resetPassword(request);

        return ResponseEntity.ok(ApiResponse.success(response, null));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Valid
            @RequestBody ChangePasswordRequestDTO request

    ) {

        String response = adminAuthService.changePassword(request);

        return ResponseEntity.ok(ApiResponse.success(response, null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {

        String response = adminAuthService.logout();

        return ResponseEntity.ok(ApiResponse.success(response, null));
    }
}