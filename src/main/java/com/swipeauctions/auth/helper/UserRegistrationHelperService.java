package com.swipeauctions.auth.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.auth.util.UserReferenceNumGenerator;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.enums.Role;
import com.swipeauctions.user.dtos.RegisterRequestDTO;
import com.swipeauctions.user.entity.OtpVerification;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.OtpVerificationRepository;
import com.swipeauctions.user.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserRegistrationHelperService {

    private final UserRepository userRepository;

    private final AdminRepository adminRepository;

    private final OtpVerificationRepository otpRepository;

    private final PasswordEncoder passwordEncoder;

    private final UserAuthHelperService authHelperService;

    private final UserReferenceNumGenerator userReferenceNumberGenerator;

    // Validate registration request
    public void validateRegistrationRequest(RegisterRequestDTO request)
    {
        String email = authHelperService.normalizeEmail(request.getEmail());

        if (!request.getPassword().equals(request.getConfirmPassword()))
        {
            throw new BadRequestException("Password and Confirm Password do not match");
        }

        User existingEmailUser = userRepository.findByEmail(email).orElse(null);

        if (existingEmailUser != null)
        {
            throw new BadRequestException(Boolean.TRUE.equals(existingEmailUser.getActive())
                    ? "Email already registered"
                    : "Email already registered but not verified. Please verify using the OTP sent earlier, or resend it.");
        }

        // Also check the separate admins table — the two tables were never cross-checked, so the same
        // email/mobile could exist in both, leaving JWT session resolution to only ever consult one
        // table's session repo for that identifier and produce inconsistent auth behavior.
        if (adminRepository.existsByEmail(email))
        {
            throw new BadRequestException("Email already registered");
        }

        User existingMobileUser = userRepository.findByMobileNumber(request.getMobileNumber()).orElse(null);

        if (existingMobileUser != null)
        {
            throw new BadRequestException(Boolean.TRUE.equals(existingMobileUser.getActive())
                    ? "Mobile number already registered"
                    : "Mobile number already registered but not verified. Please verify using the OTP sent earlier, or resend it.");
        }

        if (adminRepository.existsByMobileNumber(request.getMobileNumber()))
        {
            throw new BadRequestException("Mobile number already registered");
        }
    }

    // Create user entity
    public User createUser(RegisterRequestDTO request)
    {
        return User.builder()
                .userRefNumber(userReferenceNumberGenerator.generateUserReferenceNumber())
                .email(authHelperService.normalizeEmail(request.getEmail()))
                .mobileNumber(request.getMobileNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(resolveRole(request.getRole()))
                .build();
    }

    /** Self-registration may only choose USER or DEALER — any other value (e.g. "ADMIN") falls back to USER. */
    private Role resolveRole(String requested) {
        if (requested == null) {
            return Role.USER;
        }
        try {
            return Role.valueOf(requested.trim().toUpperCase()) == Role.DEALER ? Role.DEALER : Role.USER;
        } catch (IllegalArgumentException e) {
            return Role.USER;
        }
    }

    // Generate and store OTP details
    public void saveOtpRecord(String email, String emailOtp, String mobileOtp)
    {

        OtpVerification otpVerification = otpRepository.findByEmail(email)
                .orElse(OtpVerification.builder().email(email).build());

        otpVerification.setEmailOtp(emailOtp);

        otpVerification.setMobileOtp(mobileOtp);

        otpVerification.setEmailVerified(false);

        otpVerification.setMobileVerified(false);

        otpVerification.setEmailOtpExpiry(LocalDateTime.now().plusMinutes(10));

        otpVerification.setMobileOtpExpiry(LocalDateTime.now().plusMinutes(10));

        otpRepository.save(otpVerification);
    }

}