package com.swipeauctions.auth.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import com.swipeauctions.email.service.EmailTemplateService;
import com.swipeauctions.user.entity.User;

@Service
@RequiredArgsConstructor
public class UserEmailNotificationService {

    private final EmailService emailService;

    private final EmailTemplateService emailTemplateService;

    public void sendEmailOtp(String email, String otp)
    {
        String body = emailTemplateService.getEmailVerificationTemplate("User", otp);

        emailService.sendEmail(
                EmailRequestDTO.builder()
                        .to(email)
                        .subject("Verify Your Email")
                        .body(body)
                        .build()
        );
    }

    public void sendMobileOtp(String email, String otp)
    {
        String body = emailTemplateService.getMobileVerificationTemplate("User", otp);

        emailService.sendEmail(
                EmailRequestDTO.builder()
                        .to(email)
                        .subject("Verify Your Mobile Number")
                        .body(body)
                        .build()
        );
    }

    public void sendWelcomeEmail(User user)
    {
        String body = emailTemplateService.getWelcomeTemplate("user");

        emailService.sendEmail(
                EmailRequestDTO.builder()
                        .to(user.getEmail())
                        .subject("Welcome to SwipeAuctions")
                        .body(body)
                        .build()
        );
    }
}