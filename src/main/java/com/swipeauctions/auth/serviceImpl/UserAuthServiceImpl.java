package com.swipeauctions.auth.serviceImpl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import com.swipeauctions.user.entity.LoginOtp;
import com.swipeauctions.user.entity.OtpVerification;
import com.swipeauctions.user.entity.PasswordResetToken;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.LoginOtpRepository;
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
@Slf4j
public class UserAuthServiceImpl implements UserAuthService {

    private final UserRepository userRepository;

    private final OtpVerificationRepository otpRepository;

    private final LoginOtpRepository loginOtpRepository;

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

    private final com.swipeauctions.notification.AuctionNotificationService auctionNotificationService;

    private final com.swipeauctions.settings.service.SubscriptionService subscriptionService;


    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** Max concurrent signed-in devices per user (configurable via {@code auth.max-active-sessions}). */
    @Value("${auth.max-active-sessions:2}")
    private int maxActiveSessions;

    // Register new user account
    @Override
    @Transactional
    public String register(RegisterRequestDTO request) {

        String email = authHelperService.normalizeEmail(request.getEmail());

        registrationHelperService.validateRegistrationRequest(request);

        User user = registrationHelperService.createUser(request);

        userRepository.save(user);

        String emailOtp = otpGenerator.generateOtp();

        String mobileOtp = otpGenerator.generateOtp();

        registrationHelperService.saveOtpRecord(email, emailOtp, mobileOtp);

        emailNotificationService.sendEmailOtp(email, emailOtp);

        emailNotificationService.sendMobileOtp(user.getMobileNumber(), mobileOtp);

        return "Registration successful. OTP sent to email.";
    }
    // Max wrong-OTP guesses before requiring a fresh resend — same threshold as login lockout, closes
    // the brute-force gap on a 6-digit OTP (900,000 possibilities) that otherwise had no rate limit.
    private static final int MAX_OTP_ATTEMPTS = 5;

    //verifies email otp and activates email
    @Override
    @Transactional
    public String verifyEmailOtp(VerifyEmailOtpDTO request)
    {

        String email = authHelperService.normalizeEmail(request.getEmail());

        // Anonymous email is never confirmed/denied by a distinct error here — same "Invalid Email OTP"
        // whether the account doesn't exist or the OTP is simply wrong, so this endpoint can't be used
        // to enumerate registered accounts the way a 404-vs-400 split would.
        OtpVerification otp;
        try {
            otp = authHelperService.getOtpVerification(email);
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("Invalid Email OTP");
        }

        if (Boolean.TRUE.equals(otp.getEmailVerified()))
        {
            throw new BadRequestException("Email already verified");
        }

        if (otp.getEmailOtp() == null || !otp.getEmailOtp().equals(request.getOtp()))
        {
            int attempts = otp.getEmailOtpAttempts() + 1;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                otp.setEmailOtp(null);
                otp.setEmailOtpAttempts(0);
                otpRepository.save(otp);
                throw new BadRequestException("Too many incorrect attempts — request a new OTP");
            }
            otp.setEmailOtpAttempts(attempts);
            otpRepository.save(otp);
            throw new BadRequestException("Invalid Email OTP");
        }

        if (otp.getEmailOtpExpiry().isBefore(LocalDateTime.now()))
        {
            throw new BadRequestException("Email OTP Expired");
        }

        otp.setEmailVerified(true);

        // Optional
        otp.setEmailOtp(null);
        otp.setEmailOtpAttempts(0);

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

        OtpVerification otp;
        try {
            otp = authHelperService.getOtpVerification(email);
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException("Invalid Mobile OTP");
        }

        if (Boolean.TRUE.equals(otp.getMobileVerified()))
        {
            throw new BadRequestException("Mobile already verified");
        }

        if (otp.getMobileOtp() == null || !otp.getMobileOtp().equals(request.getOtp()))
        {
            int attempts = otp.getMobileOtpAttempts() + 1;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                otp.setMobileOtp(null);
                otp.setMobileOtpAttempts(0);
                otpRepository.save(otp);
                throw new BadRequestException("Too many incorrect attempts — request a new OTP");
            }
            otp.setMobileOtpAttempts(attempts);
            otpRepository.save(otp);
            throw new BadRequestException("Invalid Mobile OTP");
        }

        if (otp.getMobileOtpExpiry().isBefore(LocalDateTime.now()))
        {
            throw new BadRequestException("Mobile OTP Expired");
        }

        otp.setMobileVerified(true);

        // Optional
        otp.setMobileOtp(null);
        otp.setMobileOtpAttempts(0);

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

    // Same generic wording for both the real resend and the "no such account" case below, so this
    // endpoint can't be used to enumerate registered accounts (a distinguishable message/status would
    // leak registration status the same way forgotPassword deliberately avoids).
    private static final String OTP_RESENT_GENERIC_MESSAGE =
            "If an account matching this email is pending verification, a new OTP has been sent.";

    //resend otp
    @Override
    @Transactional
    public String resendOtp(ResendOtpDTO request)
    {
        String email = authHelperService.normalizeEmail(request.getEmail());

        User user;
        OtpVerification otp;
        try {
            user = authHelperService.getUserByEmail(email);
            otp = authHelperService.getOtpVerification(email);
        } catch (ResourceNotFoundException e) {
            return OTP_RESENT_GENERIC_MESSAGE;
        }

        if (otp.getLastOtpSentAt() != null
                && otp.getLastOtpSentAt().plusSeconds(30).isAfter(LocalDateTime.now()))
        {
            throw new BadRequestException("Please wait 30 seconds before requesting another OTP");
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
            otp.setEmailOtpAttempts(0);

            emailNotificationService.sendEmailOtp(user.getEmail(), emailOtp);
        }

        if (!Boolean.TRUE.equals(user.getMobileVerified()))
        {
            String mobileOtp = otpGenerator.generateOtp();

            otp.setMobileOtp(mobileOtp);

            otp.setMobileOtpExpiry(LocalDateTime.now().plusMinutes(10));
            otp.setMobileOtpAttempts(0);

            emailNotificationService.sendMobileOtp(user.getMobileNumber(), mobileOtp);
        }

        otp.setLastOtpSentAt(LocalDateTime.now());
        otpRepository.save(otp);

        return OTP_RESENT_GENERIC_MESSAGE;
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

        return issueLoginSession(user, httpServletRequest);
    }

    // Requests a one-shot login OTP, delivered to the account's email.
    @Override
    @Transactional
    public String requestLoginOtp(RequestLoginOtpDTO request)
    {
        User user = authHelperService.findUserByIdentifier(request.getEmailOrMobile());
        loginValidationService.validateUserAccountStatus(user);
        loginValidationService.validateVerificationStatus(user);

        LoginOtp existing = loginOtpRepository.findByEmail(user.getEmail()).orElse(null);

        if (existing != null && existing.getLastSentAt() != null
                && existing.getLastSentAt().plusMinutes(1).isAfter(LocalDateTime.now()))
        {
            throw new BadRequestException("Please wait 1 minute before requesting another OTP");
        }

        if (existing != null)
        {
            loginOtpRepository.delete(existing);
        }

        String code = otpGenerator.generateOtp();

        LoginOtp otp = LoginOtp.builder()
                .email(user.getEmail())
                .otp(code)
                .expiry(LocalDateTime.now().plusMinutes(5))
                .lastSentAt(LocalDateTime.now())
                .build();

        loginOtpRepository.save(otp);

        emailNotificationService.sendLoginOtp(user.getEmail(), code);

        return "OTP sent to your registered email.";
    }

    // Verifies a login OTP and, on success, issues a session identically to password login.
    @Override
    @Transactional
    public LoginResponseDTO verifyLoginOtp(VerifyLoginOtpDTO request, HttpServletRequest httpServletRequest)
    {
        User user = authHelperService.findUserByIdentifier(request.getEmailOrMobile());
        loginValidationService.validateUserAccountStatus(user);

        LoginOtp otp = loginOtpRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new BadRequestException("No OTP requested, please request a new one"));

        if (Boolean.TRUE.equals(otp.getConsumed()))
        {
            throw new BadRequestException("OTP already used, please request a new one");
        }

        if (otp.getExpiry().isBefore(LocalDateTime.now()))
        {
            throw new BadRequestException("OTP expired, please request a new one");
        }

        if (!otp.getOtp().equals(request.getOtp()))
        {
            loginValidationService.handleFailedLogin(user);
        }

        userLoginSecurityService.resetFailedLoginAttempts(user);

        loginValidationService.validateVerificationStatus(user);

        LoginResponseDTO response = issueLoginSession(user, httpServletRequest);

        // Only consume the OTP once a real token is actually issued — if the device-limit gate fires
        // instead, the same OTP can be resubmitted after the user frees a device slot, rather than
        // forcing a fresh resend.
        if (!Boolean.TRUE.equals(response.getDeviceLimitReached()))
        {
            otp.setConsumed(true);
            loginOtpRepository.save(otp);
        }

        return response;
    }

    // Shared tail of password and OTP login: device-limit gate, JWT issuance, session bookkeeping.
    private LoginResponseDTO issueLoginSession(User user, HttpServletRequest httpServletRequest)
    {
        List<UserSessions> activeSessions = sessionManagementService.getActiveSessions(user);

        if (activeSessions.size() >= maxActiveSessions)
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

        // "New device" = we've never seen a session from this User-Agent for this user (checked before
        // the new session is persisted). Alert only then, so routine logins don't spam the inbox.
        String userAgent = httpServletRequest.getHeader("User-Agent");
        boolean newDevice = userAgent == null || userAgent.isBlank()
                || !sessionRepository.existsByUserAndUserAgent(user, userAgent);

        sessionManagementService.updateUserLoginAudit(user, httpServletRequest);
        sessionManagementService.createUserSession(user, jwtId, httpServletRequest);

        if (newDevice) {
            auctionNotificationService.loginConfirmation(user.getEmail(), LocalDateTime.now(),
                    httpServletRequest.getHeader("User-Agent"), httpServletRequest.getRemoteAddr());
        }

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
                .registrationFeePaid(user.getRegistrationFeePaid())
                .subscriptionTier(subscriptionService.currentTier(user))
                .subscriptionExpiresAt(user.getSubscriptionExpiresAt())
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

        // Best-effort: this is just a confirmation notice after the password change above has already
        // fully committed (session invalidation + token consumption included) — a transient SMTP
        // failure here must not roll back a password reset that already genuinely succeeded.
        try {
            String body = emailTemplateService.getPasswordResetSuccessTemplate("User", LocalDateTime.now());
            emailService.sendEmail(EmailRequestDTO.builder()
                    .to(user.getEmail())
                    .subject("Password Reset Successfully")
                    .body(body)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send password-reset confirmation email to {}: {}", user.getEmail(), e.getMessage());
        }

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

        // Best-effort — see resetPassword's identical comment: this is a post-success confirmation,
        // not part of the actual state change, so an SMTP hiccup shouldn't undo the password change.
        try {
            String body = emailTemplateService.getPasswordChangedSuccessTemplate("User", LocalDateTime.now());
            emailService.sendEmail(EmailRequestDTO.builder()
                    .to(user.getEmail())
                    .subject("Password Changed Successfully")
                    .body(body)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send password-changed confirmation email to {}: {}", user.getEmail(), e.getMessage());
        }

        sessionManagementService.invalidateUserSessions(user);

        return "Password changed successfully, please login again";
    }

    // Logs out a specific device chosen from the login screen's device-limit prompt. The caller has
    // no JWT yet, so identity is re-proved with credentials rather than a Bearer token.
    @Override
    @Transactional
    public String logoutDevice(LogoutDeviceRequestDTO request)
    {
        User user = authHelperService.findUserByIdentifier(request.getEmailOrMobile());

        loginValidationService.validateUserAccountStatus(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
        {
            loginValidationService.handleFailedLogin(user);
        }

        userLoginSecurityService.resetFailedLoginAttempts(user);

        UserSessions session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getUser().getId().equals(user.getId()))
        {
            throw new BadRequestException("You cannot logout another user's session");
        }

        if (!Boolean.TRUE.equals(session.getActive()))
        {
            throw new BadRequestException("Session is already logged out");
        }

        session.setActive(false);

        session.setLogoutTime(LocalDateTime.now());

        sessionRepository.save(session);

        return "Device logged out. You can now sign in again.";
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

    // Registration fee payment moved to RazorpayPaymentService.createRegistrationFeeOrder /
    // verifyAndSettle (see UserAuthController) — real Razorpay payment, not a free stub.
}