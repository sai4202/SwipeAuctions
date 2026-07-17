package com.swipeauctions.auth.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

        if (existingEmailUser != null && Boolean.TRUE.equals(existingEmailUser.getActive()))
        {
            throw new BadRequestException("Email already registered");
        }

        User existingMobileUser = userRepository.findByMobileNumber(request.getMobileNumber()).orElse(null);

        if (existingMobileUser != null && Boolean.TRUE.equals(existingMobileUser.getActive()))
        {
            throw new BadRequestException("Mobile number already registered");
        }

        if (existingEmailUser != null && existingMobileUser != null && !existingEmailUser.getId().equals(existingMobileUser.getId()))
        {
            throw new BadRequestException("Email and mobile belong to different accounts");
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
                .role(Role.USER)
                .build();
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