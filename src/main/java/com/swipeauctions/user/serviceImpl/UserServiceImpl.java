package com.swipeauctions.user.serviceImpl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.auth.util.OtpGenerator;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import com.swipeauctions.email.service.EmailTemplateService;
import com.swipeauctions.sms.service.SmsService;
import com.swipeauctions.user.dtos.*;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.entity.UserUpdateRequest;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.user.repository.UserUpdateRequestRepository;
import com.swipeauctions.user.service.UserSecurityService;
import com.swipeauctions.user.service.UserService;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService
{
    private final UserRepository userRepository;

    private final UserUpdateRequestRepository userUpdateRequestRepository;

    private final EmailService emailService;

    private final EmailTemplateService emailTemplateService;

    private final SmsService smsService;

    private final OtpGenerator otpGenerator;

    private final UserSecurityService userSecurityService;

    // Max wrong-OTP guesses before requiring a fresh resend — closes the brute-force gap on a 6-digit
    // OTP (900,000 possibilities) that otherwise had no rate limit.
    private static final int MAX_OTP_ATTEMPTS = 5;

    private String normalizeEmail(String email)
    {
        return email.trim().toLowerCase();
    }

    @Override
    public String requestEmailChange(ChangeEmailRequestDTO request,
                                     String email)
    {
        email = normalizeEmail(email);

        String newEmail = normalizeEmail(request.getNewEmail());

        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!Boolean.TRUE.equals(user.getActive()))
        {
            throw new BadRequestException("Account is not active");
        }

        if (user.getEmail().equalsIgnoreCase(newEmail))
        {
            throw new BadRequestException("New email cannot be same as current email");
        }

        if (userRepository.existsByEmail(newEmail))
        {
            throw new BadRequestException("Email already registered");
        }

        UserUpdateRequest updateRequest = userUpdateRequestRepository.findByUser(user)
                        .orElse(UserUpdateRequest.builder()
                                .user(user)
                                .build());

        // Cooldown check.
        if (updateRequest.getEmailOtpSentAt() != null && updateRequest.getEmailOtpSentAt().plusMinutes(1).isAfter(LocalDateTime.now()))
        {
            throw new BadRequestException("Please wait 1 minute before requesting another OTP");
        }

        String otp = otpGenerator.generateOtp();

        updateRequest.setNewEmail(newEmail);
        updateRequest.setEmailOtp(otp);
        updateRequest.setEmailOtpExpiry(
                LocalDateTime.now().plusMinutes(10));
        updateRequest.setEmailVerified(false);
        updateRequest.setEmailOtpSentAt(LocalDateTime.now());
        updateRequest.setEmailOtpAttempts(0);

        userUpdateRequestRepository.save(updateRequest);

        String body = emailTemplateService.getEmailVerificationTemplate("User", otp);

        emailService.sendEmail(EmailRequestDTO.builder()
                        .to(newEmail)
                        .subject("Email Change Verification")
                        .body(body)
                        .build());

        return "OTP sent to new email successfully";
    }


    @Override
    public String verifyEmailChange(VerifyEmailChangeDTO request, String email)
    {
        email = normalizeEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserUpdateRequest updateRequest = userUpdateRequestRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("No email update request found"));

        if (updateRequest.getEmailOtp() == null)
        {
            throw new BadRequestException("No email verification request found");
        }

        if (updateRequest.getEmailOtpExpiry() == null)
        {
            throw new BadRequestException("No email verification request found");
        }

        if (updateRequest.getEmailOtpExpiry().isBefore(LocalDateTime.now()))
        {
            userUpdateRequestRepository.delete(updateRequest);

            throw new BadRequestException("OTP expired");
        }

        if (!updateRequest.getEmailOtp().equals(request.getOtp()))
        {
            int attempts = updateRequest.getEmailOtpAttempts() + 1;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                updateRequest.setEmailOtp(null);
                updateRequest.setEmailOtpAttempts(0);
                userUpdateRequestRepository.save(updateRequest);
                throw new BadRequestException("Too many incorrect attempts — request a new OTP");
            }
            updateRequest.setEmailOtpAttempts(attempts);
            userUpdateRequestRepository.save(updateRequest);
            throw new BadRequestException("Invalid OTP");
        }
        updateRequest.setEmailOtpAttempts(0);

        if (updateRequest.getNewEmail() == null)
        {
            throw new BadRequestException("No pending email change request found");
        }

        // Recheck before update.
        if (userRepository.existsByEmail(updateRequest.getNewEmail()))
        {
            throw new BadRequestException("Email already registered");
        }


        user.setEmail(updateRequest.getNewEmail());

        userRepository.save(user);

        userSecurityService.invalidateUserSessions(user);

        userUpdateRequestRepository.delete(updateRequest);

        return "Email updated successfully. Please login again using your new email.";
    }

    @Override
    public String resendEmailChangeOtp(String email)
    {
        email = normalizeEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserUpdateRequest updateRequest = userUpdateRequestRepository.findByUser(user)
                        .orElseThrow(() -> new ResourceNotFoundException("No email update request found"));

        if (updateRequest.getNewEmail() == null)
        {
            throw new BadRequestException("No pending email change request found");
        }

        // Cooldown check.
        if (updateRequest.getEmailOtpSentAt() != null && updateRequest.getEmailOtpSentAt().plusMinutes(1).isAfter(LocalDateTime.now()))
        {
            throw new BadRequestException("Please wait 1 minute before requesting another OTP");
        }

        // Check whether requested email is still available.
        if (userRepository.existsByEmail(updateRequest.getNewEmail()))
        {
            throw new BadRequestException("Requested email is already registered");
        }

        String otp = otpGenerator.generateOtp();

        updateRequest.setEmailOtp(otp);

        updateRequest.setEmailOtpExpiry(LocalDateTime.now().plusMinutes(10));

        updateRequest.setEmailVerified(false);

        updateRequest.setEmailOtpSentAt(LocalDateTime.now());

        updateRequest.setEmailOtpAttempts(0);

        userUpdateRequestRepository.save(updateRequest);

        String body = emailTemplateService.getEmailVerificationTemplate("User", otp);

        emailService.sendEmail(
                EmailRequestDTO.builder()
                        .to(updateRequest.getNewEmail())
                        .subject("Email Change Verification")
                        .body(body)
                        .build()
        );

        return "OTP resent successfully";
    }

    @Override
    public String requestMobileChange(ChangeMobileRequestDTO request, String email)
    {
        email = normalizeEmail(email);

        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!Boolean.TRUE.equals(user.getActive()))
        {
            throw new BadRequestException("Account is not active");
        }

        if (user.getMobileNumber().equals(request.getNewMobileNumber()))
        {
            throw new BadRequestException("New mobile number cannot be same as current mobile number");
        }

        if (userRepository.existsByMobileNumber(request.getNewMobileNumber()))
        {
            throw new BadRequestException("Mobile number already registered");
        }

        UserUpdateRequest updateRequest = userUpdateRequestRepository.findByUser(user)
                        .orElse(UserUpdateRequest.builder()
                                .user(user)
                                .build());

        if (updateRequest.getMobileOtpSentAt() != null &&
                updateRequest.getMobileOtpSentAt().plusMinutes(1).isAfter(LocalDateTime.now()))
        {
            throw new BadRequestException("Please wait 1 minute before requesting another OTP");
        }

        String otp = otpGenerator.generateOtp();

        updateRequest.setNewMobileNumber(request.getNewMobileNumber());

        updateRequest.setMobileOtp(otp);

        updateRequest.setMobileOtpExpiry(LocalDateTime.now().plusMinutes(10));

        updateRequest.setMobileVerified(false);

        updateRequest.setMobileOtpSentAt(LocalDateTime.now());

        updateRequest.setMobileOtpAttempts(0);

        userUpdateRequestRepository.save(updateRequest);

        smsService.sendSms(request.getNewMobileNumber(), "Your SwipeAuctions mobile verification code is " + otp + ". Valid for 10 minutes.");

        return "OTP sent successfully";
    }

    @Override
    public String verifyMobileChange(VerifyMobileChangeDTO request, String email)
    {
        email = normalizeEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserUpdateRequest updateRequest = userUpdateRequestRepository.findByUser(user)
                        .orElseThrow(() -> new ResourceNotFoundException("No mobile update request found"));

        if (updateRequest.getMobileOtp() == null)
        {
            throw new BadRequestException("No mobile verification request found");
        }

        if (updateRequest.getMobileOtpExpiry() == null)
        {
            throw new BadRequestException("No mobile verification request found");
        }

        if (updateRequest.getMobileOtpExpiry().isBefore(LocalDateTime.now()))
        {
            userUpdateRequestRepository.delete(updateRequest);

            throw new BadRequestException("OTP expired");
        }

        if (!updateRequest.getMobileOtp().equals(request.getOtp()))
        {
            int attempts = updateRequest.getMobileOtpAttempts() + 1;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                updateRequest.setMobileOtp(null);
                updateRequest.setMobileOtpAttempts(0);
                userUpdateRequestRepository.save(updateRequest);
                throw new BadRequestException("Too many incorrect attempts — request a new OTP");
            }
            updateRequest.setMobileOtpAttempts(attempts);
            userUpdateRequestRepository.save(updateRequest);
            throw new BadRequestException("Invalid OTP");
        }
        updateRequest.setMobileOtpAttempts(0);

        if (updateRequest.getNewMobileNumber() == null)
        {
            throw new BadRequestException("No pending mobile change request found");
        }

        if (userRepository.existsByMobileNumber(updateRequest.getNewMobileNumber()))
        {
            throw new BadRequestException("Mobile number already registered");
        }


        user.setMobileNumber(updateRequest.getNewMobileNumber());

        userRepository.save(user);

        userUpdateRequestRepository.delete(updateRequest);

        return "Mobile number updated successfully";
    }

    @Override
    public String resendMobileChangeOtp(String email)
    {
        email = normalizeEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserUpdateRequest updateRequest = userUpdateRequestRepository.findByUser(user)
                        .orElseThrow(() -> new ResourceNotFoundException("No mobile update request found"));

        if (updateRequest.getNewMobileNumber() == null)
        {
            throw new BadRequestException("No pending mobile change request found");
        }

        // Cooldown check.
        if (updateRequest.getMobileOtpSentAt() != null &&
                updateRequest.getMobileOtpSentAt().plusMinutes(1).isAfter(LocalDateTime.now()))
        {
            throw new BadRequestException("Please wait 1 minute before requesting another OTP");
        }

        // Check whether requested mobile is still available.
        if (userRepository.existsByMobileNumber(updateRequest.getNewMobileNumber()))
        {
            throw new BadRequestException("Requested mobile number is already registered");
        }

        String otp = otpGenerator.generateOtp();

        updateRequest.setMobileOtp(otp);

        updateRequest.setMobileOtpExpiry(LocalDateTime.now().plusMinutes(10));

        updateRequest.setMobileVerified(false);

        updateRequest.setMobileOtpSentAt(LocalDateTime.now());

        userUpdateRequestRepository.save(updateRequest);

        smsService.sendSms(updateRequest.getNewMobileNumber(), "Your SwipeAuctions mobile verification code is " + otp + ". Valid for 10 minutes.");

        return "OTP resent successfully";
    }

    @Override
    public UserProfileResponseDTO getProfile(String email)
    {
        email = normalizeEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserProfileResponseDTO.builder()
                .id(user.getId())
                .userReferenceNumber(
                        user.getUserRefNumber()
                )
                .email(user.getEmail())
                .mobileNumber(
                        user.getMobileNumber()
                )
                .role(
                        user.getRole().name()
                )
                .active(
                        user.getActive()
                )
                .emailVerified(
                        user.getEmailVerified()
                )
                .mobileVerified(
                        user.getMobileVerified()
                )
                .kycCompleted(
                        user.getKycCompleted()
                )
                .build();
    }

}