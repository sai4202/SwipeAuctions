package com.swipeauctions.admin.serviceImpl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.swipeauctions.admin.dtos.AdminLoginRequestDTO;
import com.swipeauctions.admin.dtos.AdminLoginResponseDTO;
import com.swipeauctions.admin.dtos.AdminRegisterRequestDTO;
import com.swipeauctions.admin.entity.Admin;
import com.swipeauctions.admin.entity.AdminPasswordResetToken;
import com.swipeauctions.admin.helper.AdminLoginValidationService;
import com.swipeauctions.admin.helper.AdminRegistrationHelperService;
import com.swipeauctions.admin.helper.AdminSessionManagementService;
import com.swipeauctions.admin.repository.AdminPasswordResetTokenRepository;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.admin.service.AdminAuthService;
import com.swipeauctions.admin.service.AdminLoginSecurityService;
import com.swipeauctions.auth.dto.ChangePasswordRequestDTO;
import com.swipeauctions.auth.dto.ForgotPasswordRequestDTO;
import com.swipeauctions.auth.dto.ResetPasswordRequestDTO;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.security.jwt.JwtService;
import com.swipeauctions.common.util.LoggedInUserUtil;
import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import com.swipeauctions.email.service.EmailTemplateService;
import com.swipeauctions.session.entity.AdminSessions;
import com.swipeauctions.session.repository.AdminSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import com.swipeauctions.common.exception.ResourceNotFoundException;

import java.util.UUID;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AdminAuthServiceImpl implements AdminAuthService {

    private final JwtService jwtService;

    private final AdminSessionRepository adminSessionRepository;

    private final AdminRepository adminRepository;

    private final PasswordEncoder passwordEncoder;

    private final LoggedInUserUtil loggedInUserUtil;

    private final AdminLoginSecurityService adminSecurityService;

    private final AdminPasswordResetTokenRepository adminPasswordResetTokenRepository;

    private final EmailService emailService;

    private final EmailTemplateService emailTemplateService;

    private final AdminRegistrationHelperService registrationHelperService;

    private final AdminLoginValidationService loginValidationService;

    private final AdminSessionManagementService sessionManagementService;

    //reset password variable storing link
    @Value("${app.frontend.url}")
    private String frontendUrl;

    // Admin session timeout (user decision 2026-07-21): shorter than the regular user JWT.
    @Value("${admin.jwt.expiration}")
    private long adminJwtExpiration;

    //Registers a new admin account.
    @Override
    public String register(AdminRegisterRequestDTO request)
    {
        // Validate registration request.
        registrationHelperService.validateAdminRegistration(request);

        // Create admin entity.
        Admin admin = registrationHelperService.createAdmin(request);

        // Persist admin.
        adminRepository.save(admin);

        return "Admin registered successfully";
    }


    // Authenticates admin and creates session.
    @Override
    public AdminLoginResponseDTO login(AdminLoginRequestDTO request, HttpServletRequest httpServletRequest)
    {

        // Normalize email before authentication.
        String email = request.getEmail().trim().toLowerCase();

        // Fetch admin details.
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        // Validate account status.
        loginValidationService.validateAdminAccountStatus(admin);

        // Validate password.
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword()))
        {
            // Track failed login attempts.
            loginValidationService.handleFailedLogin(admin);
        }
        // Restrict admin to a single active session.
        sessionManagementService.validateAdminSessionLimit(admin);

        // Reset failed attempts after successful login.
        adminSecurityService.resetFailedLoginAttempts(admin);

        // Generate unique JWT identifier.
        String jwtId = jwtService.generateJwtId();

        // Generate JWT token (short-lived — 2-minute admin session timeout).
        String token = jwtService.generateToken(admin.getEmail(), jwtId, adminJwtExpiration);

        // Create admin session.
        sessionManagementService.createAdminSession(admin, httpServletRequest, jwtId);

        // Update login audit details.
        sessionManagementService.updateAdminLoginAudit(admin, httpServletRequest);

        // Persist admin audit and account updates.
        adminRepository.save(admin);

        // Build login response.
        return buildLoginResponse(admin, token);
    }

    private AdminLoginResponseDTO buildLoginResponse(Admin admin, String token) {

        return AdminLoginResponseDTO.builder()

                .adminId(admin.getId())

                .email(admin.getEmail())

                .token(token)

                .tokenType("Bearer")

                .role(admin.getRole().name())

                .active(admin.getActive())

                .build();
    }


    // Forgot Password
    @Override
    public String forgotPassword(ForgotPasswordRequestDTO request)
    {

        // Normalize email.
        String email = request.getEmail().trim().toLowerCase();

        // Validate admin existence.
        Admin admin = adminRepository.findByEmail(email).orElse(null);
        if(admin == null){
            return "If an account exists, a password reset link has been sent successfully";
        }

        AdminPasswordResetToken resetToken = adminPasswordResetTokenRepository.findByAdmin(admin)
                        .orElse(AdminPasswordResetToken.builder()
                                .admin(admin)
                                .build()
                        );

        resetToken.setToken(UUID.randomUUID().toString());

        resetToken.setExpiryTime(LocalDateTime.now().plusMinutes(15));

        resetToken.setUsed(false);

        adminPasswordResetTokenRepository.save(resetToken);

        // Build frontend reset URL.
        String resetLink = frontendUrl + "/admin/reset-password?token=" + resetToken.getToken();

        // Generate email body.
        String body = emailTemplateService.getPasswordResetTemplate(admin.getFirstName(), resetLink);

        // Send reset email.
        EmailRequestDTO emailRequest = EmailRequestDTO.builder()

                        .to(admin.getEmail())

                        .subject("Admin Password Reset Request")

                        .body(body)

                        .build();

        emailService.sendEmail(emailRequest);

        return "Password reset link has been sent successfully";
    }

    // Reset Password
    @Override
    public String resetPassword(ResetPasswordRequestDTO request)
    {

        if (!request.getNewPassword().equals(request.getConfirmPassword()))
        {
            throw new BadRequestException("Password and Confirm Password do not match");
        }

        AdminPasswordResetToken resetToken = adminPasswordResetTokenRepository.findByToken(request.getToken())
                        .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (Boolean.TRUE.equals(resetToken.getUsed()))
        {
            throw new BadRequestException("Reset token has already been used");
        }

        if (resetToken.getExpiryTime().isBefore(LocalDateTime.now()))
        {
            // Remove expired token from DB
            adminPasswordResetTokenRepository.delete(resetToken);
            throw new BadRequestException("Reset token has expired");
        }

        Admin admin = resetToken.getAdmin();

        // Prevent reusing old password.
        if (passwordEncoder.matches(request.getNewPassword(), admin.getPassword()))
        {
            throw new BadRequestException("New password cannot be same as old password");
        }

        // Prevents the name is a password
        if (request.getNewPassword().toLowerCase().contains(admin.getFirstName().toLowerCase()))
        {
            throw new BadRequestException("Password should not contain your name");
        }

        admin.setPassword(passwordEncoder.encode(request.getNewPassword()));

        admin.setPasswordChangedAt(LocalDateTime.now());

        adminRepository.save(admin);

        // Mark token as used.
        resetToken.setUsed(true);

        adminPasswordResetTokenRepository.save(resetToken);

        // Best-effort — the password change above already fully committed, so a transient SMTP
        // failure on this confirmation notice must not roll it back.
        try {
            String body = emailTemplateService
                    .getPasswordResetSuccessTemplate(admin.getLastName(), LocalDateTime.now());
            emailService.sendEmail(EmailRequestDTO.builder()
                    .to(admin.getEmail())
                    .subject("Password Reset Successfully")
                    .body(body)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send admin password-reset confirmation email to {}: {}", admin.getEmail(), e.getMessage());
        }

        // Force logout active admin session.
        adminSessionRepository.findByAdminAndActiveTrue(admin)
                .ifPresent(session -> {
                    session.setActive(false);

                    session.setLogoutTime(LocalDateTime.now());
                    adminSessionRepository.save(session);
                });

        return "Password reset successfully. Please login again.";
    }

    // Change Password
    @Override
    public String changePassword(ChangePasswordRequestDTO request) {

        // Fetch currently logged-in admin.
        Admin admin = loggedInUserUtil.getCurrentAdmin();

        // Validate current password.
        if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPassword()))
        {
            throw new BadRequestException("Current password is incorrect");
        }

        // Validate new password confirmation.
        if (!request.getNewPassword().equals(request.getConfirmPassword()))
        {
            throw new BadRequestException("New password and confirm password do not match");
        }

        // Prevent reusing current password.
        if (passwordEncoder.matches(request.getNewPassword(), admin.getPassword()))
        {
            throw new BadRequestException("New password cannot be same as current password");
        }

        //prevents the storing the first name of the admin
        if (request.getNewPassword().toLowerCase().contains(admin.getFirstName().toLowerCase()))
        {
            throw new BadRequestException("Password should not contain your name");
        }

        // Update password.
        admin.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // Update password audit timestamp.
        admin.setPasswordChangedAt(LocalDateTime.now());

        adminRepository.save(admin);

        // Best-effort — see resetPassword's identical comment above.
        try {
            String body = emailTemplateService
                    .getPasswordChangedSuccessTemplate(admin.getLastName(), LocalDateTime.now());
            emailService.sendEmail(EmailRequestDTO.builder()
                    .to(admin.getEmail())
                    .subject("Password Changed Successfully")
                    .body(body)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send admin password-changed confirmation email to {}: {}", admin.getEmail(), e.getMessage());
        }

        // Invalidate active session and force re-login.
        adminSessionRepository.findByAdminAndActiveTrue(admin)
                .ifPresent(session -> {

                    session.setActive(false);

                    session.setLogoutTime(LocalDateTime.now());

                    adminSessionRepository.save(session);
                });

        return "Password changed successfully. Please login again.";
    }


    //Logs out the currently authenticated admin.
    @Override
    public String logout() {

        Admin admin = loggedInUserUtil.getCurrentAdmin();

        AdminSessions adminSession = adminSessionRepository.findByAdminAndActiveTrue(admin)
                        .orElseThrow(() -> new ResourceNotFoundException("Active session not found"));

        // Mark session as inactive.
        adminSession.setActive(false);

        // Track logout timestamp.
        adminSession.setLogoutTime(LocalDateTime.now());
        adminSessionRepository.save(adminSession);

        return "Admin logged out successfully";
    }
}