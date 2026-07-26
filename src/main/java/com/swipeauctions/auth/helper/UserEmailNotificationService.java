package com.swipeauctions.auth.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import com.swipeauctions.email.service.EmailTemplateService;
import com.swipeauctions.sms.service.SmsService;
import com.swipeauctions.user.entity.User;

@Service
@RequiredArgsConstructor
public class UserEmailNotificationService {

    private final EmailService emailService;

    private final EmailTemplateService emailTemplateService;

    private final SmsService smsService;

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

    public void sendMobileOtp(String mobileNumber, String otp)
    {
        smsService.sendSms(mobileNumber, "Your SwipeAuctions mobile verification code is " + otp + ". Valid for 10 minutes.");
    }

    public void sendLoginOtp(String email, String otp)
    {
        String body = emailTemplateService.getEmailVerificationTemplate("User", otp);

        emailService.sendEmail(
                EmailRequestDTO.builder()
                        .to(email)
                        .subject("Your SwipeAuctions Login Code")
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