package com.swipeauctions.auth.serviceImpl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.swipeauctions.auth.dto.*;
import com.swipeauctions.auth.helper.*;
import com.swipeauctions.auth.service.UserAuthService;
import com.swipeauctions.auth.service.UserLoginSecurityService;
import com.swipeauctions.auth.util.OtpGenerator;

import com.swipeauctions.auth.util.UserReferenceNumGenerator;
import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import com.swipeauctions.email.service.EmailTemplateService;
import com.swipeauctions.session.dtos.SessionResponseDTO;
import com.swipeauctions.user.dtos.RegisterRequestDTO;
import com.swipeauctions.user.entity.OtpVerification;
import com.swipeauctions.user.entity.PasswordResetToken;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.OtpVerificationRepository;
import com.swipeauctions.user.repository.PasswordResetTokenRepository;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.common.security.jwt.JwtService;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.common.exception.UnauthorizedException;
import com.swipeauctions.session.entity.UserSessions;
import com.swipeauctions.session.repository.UserSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Handles users authentication business logic
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final UserRepository userRepository;

    private final OtpVerificationRepository otpRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private final EmailService emailService;

    private final EmailTemplateService emailTemplateService;

    private final OtpGenerator otpGenerator;

    private final UserSessionRepository sessionRepository;

    private final UserLoginSecurityService userLoginSecurityService;

    private final UserAuthHelperService authHelperService;

    private final UserSessionManagementService sessionManagementService;

    private final UserLoginValidationService loginValidationService;

    private final UserEmailNotificationService emailNotificationService;

    private final UserRegistrationHelperService registrationHelperService;

    private final UserReferenceNumGenerator userReferenceNumberGenerator;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // Register new user account
    @Override
    @Transactional
    public String register(RegisterRequestDTO request) {

        String email = authHelperService.normalizeEmail(request.getEmail());

        registrationHelperService.validateRegistrationRequest(request);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            user = userRepository.findByMobileNumber(request.getMobileNumber()).orElse(null);
        }

        if (user == null)
        {
            user = registrationHelperService.createUser(request);
        }
        else {
            // Existing inactive account.
            user.setPassword(passwordEncoder.encode(request.getPassword()));

            user.setEmailVerified(false);

            user.setMobileVerified(false);

            user.setActive(false);
        }

        userRepository.save(user);

        String emailOtp = otpGenerator.generateOtp();

        String mobileOtp = otpGenerator.generateOtp();

        registrationHelperService.saveOtpRecord(email, emailOtp, mobileOtp);

        emailNotificationService.sendEmailOtp(email, emailOtp);

        emailNotificationService.sendMobileOtp(email, mobileOtp);

        return "Registration successful. OTP sent to email.";
    }
    //verifies email otp and activates email
    @Override
    @Transactional
    public String verifyEmailOtp(VerifyEmailOtpDTO request)
    {

        String email = authHelperService.normalizeEmail(request.getEmail());

        OtpVerification otp = authHelperService.getOtpVerification(email);

        if (Boolean.TRUE.equals(otp.getEmailVerified()))
        {
            throw new BadRequestException("Email already verified");
        }

        if (otp.getEmailOtp() == null || !otp.getEmailOtp().equals(request.getOtp()))
        {
            throw new BadRequestException("Invalid Email OTP");
        }

        if (otp.getEmailOtpExpiry().isBefore(LocalDateTime.now()))
        {
            throw new BadRequestException("Email OTP Expired");
        }

        otp.setEmailVerified(true);

        // Optional
        otp.setEmailOtp(null);

        otpRepository.save(otp);

        User user = authHelperService.getUserByEmail(email);

        user.setEmailVerified(true);

        userRepository.save(user);

        return "Email verified successfully";
    }

    //verifies mobile otp and activates account
    @Override
    @Transactional
    public String verifyMobileOtp(VerifyMobileOtpDTO request)
    {

        String email = authHelperService.normalizeEmail(request.getEmail());

        OtpVerification otp = authHelperService.getOtpVerification(email);

        if (Boolean.TRUE.equals(otp.getMobileVerified()))
        {
            throw new BadRequestException("Mobile already verified");
        }

        if (otp.getMobileOtp() == null || !otp.getMobileOtp().equals(request.getOtp()))
        {
            throw new BadRequestException("Invalid Mobile OTP");
        }

        if (otp.getMobileOtpExpiry().isBefore(LocalDateTime.now()))
        {
            throw new BadRequestException("Mobile OTP Expired");
        }

        otp.setMobileVerified(true);

        // Optional
        otp.setMobileOtp(null);

        otpRepository.save(otp);

        User user = authHelperService.getUserByEmail(email);

        boolean wasAlreadyActive = Boolean.TRUE.equals(user.getActive());

        user.setMobileVerified(true);

        if (Boolean.TRUE.equals(user.getEmailVerified()))
        {
            user.setActive(true);
        }

        userRepository.save(user);

        if (Boolean.TRUE.equals(user.getEmailVerified()) && Boolean.TRUE.equals(user.getMobileVerified()) && Boolean.TRUE.equals(user.getActive()))
        {
            otpRepository.delete(otp);
        }

        if (!wasAlreadyActive && Boolean.TRUE.equals(user.getActive()))
        {
            emailNotificationService.sendWelcomeEmail(user);
        }

        return "Mobile verified successfully";
    }

    //resend otp
    @Override
    @Transactional
    public String resendOtp(ResendOtpDTO request)
    {
        String email = authHelperService.normalizeEmail(request.getEmail());

        User user = authHelperService.getUserByEmail(email);

        OtpVerification otp = authHelperService.getOtpVerification(email);

        if (otp.getLastOtpSentAt() != null
                && otp.getLastOtpSentAt().plusMinutes(1).isAfter(LocalDateTime.now()))
        {
            throw new BadRequestException("Please wait 1 minute before requesting another OTP");
        }

        if (Boolean.TRUE.equals(user.getEmailVerified()) && Boolean.TRUE.equals(user.getMobileVerified()))
        {
            throw new BadRequestException("User already verified");
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified()))
        {
            String emailOtp = otpGenerator.generateOtp();

            otp.setEmailOtp(emailOtp);

            otp.setEmailOtpExpiry(LocalDateTime.now().plusMinutes(10));

            emailNotificationService.sendEmailOtp(user.getEmail(), emailOtp);
        }

        if (!Boolean.TRUE.equals(user.getMobileVerified()))
        {
            String mobileOtp = otpGenerator.generateOtp();

            otp.setMobileOtp(mobileOtp);

            otp.setMobileOtpExpiry(LocalDateTime.now().plusMinutes(10));

            emailNotificationService.sendMobileOtp(user.getEmail(), mobileOtp);
        }

        otp.setLastOtpSentAt(LocalDateTime.now());
        otpRepository.save(otp);

        return "OTP resent successfully";
    }


    //Login User and JWT token Generation
    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request, HttpServletRequest httpServletRequest)
    {

        User user = authHelperService.findUserByIdentifier(request.getEmailOrMobile());
        loginValidationService.validateUserAccountStatus(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
        {
            loginValidationService.handleFailedLogin(user);
        }

        userLoginSecurityService.resetFailedLoginAttempts(user);

        loginValidationService.validateVerificationStatus(user);

        List<UserSessions> activeSessions = sessionManagementService.getActiveSessions(user);

        if (activeSessions.size() >= 2)
        {
            return LoginResponseDTO.builder()
                    .deviceLimitReached(true)
                    .message("Maximum device limit reached. Please logout one device to continue.")
                    .activeSessions(activeSessions.stream().map(session ->
                                    SessionResponseDTO.builder()
                                            .sessionId(session.getId())
                                            .deviceId(session.getDeviceId())
                                            .deviceName(session.getDeviceName())
                                            .ipAddress(session.getIpAddress())
                                            .loginTime(session.getLoginTime())
                                            .lastActivityTime(session.getLastActivityTime())
                                            .active(session.getActive())
                                            .build())
                                    .toList()
                    ).build();
        }

        String jwtId = jwtService.generateJwtId();

        String token = jwtService.generateToken(user.getEmail(), jwtId);

        sessionManagementService.updateUserLoginAudit(user, httpServletRequest);
        sessionManagementService.createUserSession(user, jwtId, httpServletRequest);

        return LoginResponseDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .token(token)
                .tokenType("Bearer")
                .role(user.getRole().name())
                .active(user.getActive())
                .emailVerified(user.getEmailVerified())
                .mobileVerified(user.getMobileVerified())
                .kycCompleted(user.getKycCompleted())
                .build();
    }

    //sends password reset mail
    @Override
    @Transactional
    public String forgotPassword(ForgotPasswordRequestDTO request)
    {
        String email = authHelperService.normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(email).orElse(null);

        // Prevent user enumeration attacks.
        if (user == null)
        {
            return "If an account exists, a password reset link has been sent successfully";
        }

        if (!Boolean.TRUE.equals(user.getActive()))
        {
            throw new BadRequestException("Account is not active");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByUser(user)
                .orElse(PasswordResetToken.builder().user(user).build());

        resetToken.setToken(UUID.randomUUID().toString());

        resetToken.setExpiryTime(LocalDateTime.now().plusMinutes(15));

        resetToken.setUsed(false);

        passwordResetTokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/reset-password?token=" + resetToken.getToken();

        String htmlContent = emailTemplateService.getPasswordResetTemplate("User", resetLink);

        EmailRequestDTO emailRequest = EmailRequestDTO.builder()
                .to(user.getEmail())
                .subject("Reset Your Password")
                .body(htmlContent)
                .build();

        emailService.sendEmail(emailRequest);

        return "Password reset link has been sent successfully";
    }

    //Reset password using reset token
    @Override
    @Transactional
    public String resetPassword(ResetPasswordRequestDTO request)
    {

        if (!request.getNewPassword().equals(request.getConfirmPassword()))
        {
            throw new BadRequestException("Password and Confirm Password do not match");
        }

        PasswordResetToken passwordResetToken = authHelperService.getPasswordResetToken(request.getToken());

        if (Boolean.TRUE.equals(passwordResetToken.getUsed()))
        {
            throw new BadRequestException("Reset link already used");
        }

        if (passwordResetToken.getExpiryTime().isBefore(LocalDateTime.now()))
        {
            // Remove expired token from DB
            passwordResetTokenRepository.delete(passwordResetToken);
            throw new BadRequestException("Reset link has expired");
        }

        User user = passwordResetToken.getUser();

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword()))
        {
            throw new BadRequestException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        user.setPasswordChangedAt(LocalDateTime.now());

        userRepository.save(user);

        sessionManagementService.invalidateUserSessions(user);

        passwordResetToken.setUsed(true);

        passwordResetTokenRepository.save(passwordResetToken);

        String body = emailTemplateService.getPasswordResetSuccessTemplate("User", LocalDateTime.now());

        EmailRequestDTO emailRequest = EmailRequestDTO.builder()
                .to(user.getEmail())
                .subject("Password Reset Successfully")
                .body(body)
                .build();

        emailService.sendEmail(emailRequest);

        return "Password reset successfully";
    }

    //change password after login
    @Override
    @Transactional
    public String changePassword(ChangePasswordRequestDTO request, String email)
    {
        email = authHelperService.normalizeEmail(email);

        User user = authHelperService.getUserByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword()))
        {
            throw new UnauthorizedException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword()))
        {
            throw new BadRequestException("Password and Confirm Password do not match");
        }

        // New password should be different from current password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword()))
        {
            throw new BadRequestException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        user.setPasswordChangedAt(LocalDateTime.now());

        userRepository.save(user);

        String body = emailTemplateService.getPasswordChangedSuccessTemplate("User", LocalDateTime.now());

        EmailRequestDTO emailRequest = EmailRequestDTO.builder()
                .to(user.getEmail())
                .subject("Password Changed Successfully")
                .body(body)
                .build();

        emailService.sendEmail(emailRequest);

        sessionManagementService.invalidateUserSessions(user);

        return "Password changed successfully, please login again";
    }

    //logout functionality
    @Override
    @Transactional
    public String logout(String token)
    {
        // Extract jwtId from token
        String jwtId = jwtService.extractJwtId(token);

        // Find active session
        UserSessions session = sessionRepository.findByJwtIdAndActiveTrue(jwtId)
                        .orElseThrow(() -> new ResourceNotFoundException("Active session not found"));

        // Mark session inactive
        session.setActive(false);

        // Store logout time
        session.setLogoutTime(LocalDateTime.now());

        // Save updated session
        sessionRepository.save(session);

        // Clear Spring Security Context
        SecurityContextHolder.clearContext();

        return "Logged out successfully";
    }
}