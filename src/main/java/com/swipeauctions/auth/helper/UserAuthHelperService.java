package com.swipeauctions.auth.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.user.entity.OtpVerification;
import com.swipeauctions.user.entity.PasswordResetToken;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.OtpVerificationRepository;
import com.swipeauctions.user.repository.PasswordResetTokenRepository;
import com.swipeauctions.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserAuthHelperService {

    private final UserRepository userRepository;

    private final OtpVerificationRepository otpRepository;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public String normalizeEmail(String email)
    {
        return email.trim().toLowerCase();
    }

    public User getUserByEmail(String email)
    {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public OtpVerification getOtpVerification(String email)
    {
        return otpRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("OTP record not found"));
    }

    public PasswordResetToken getPasswordResetToken(String token)
    {
        return passwordResetTokenRepository.findByToken(token).orElseThrow(() -> new BadRequestException("Invalid reset token"));
    }

    public User findUserByIdentifier(String identifier)
    {
        identifier = identifier.trim();

        if (identifier.contains("@"))
        {
            identifier = normalizeEmail(identifier);
        }

        final String normalizedIdentifier = identifier;

        return userRepository.findByEmail(normalizedIdentifier)
                .orElseGet(() -> userRepository.findByMobileNumber(normalizedIdentifier)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }
}
