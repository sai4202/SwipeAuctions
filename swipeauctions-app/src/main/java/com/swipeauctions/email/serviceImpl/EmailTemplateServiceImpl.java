package com.swipeauctions.email.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import com.swipeauctions.email.service.EmailTemplateService;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailTemplateServiceImpl
        implements EmailTemplateService {

    private final TemplateEngine templateEngine;

    @Override
    public String getEmailVerificationTemplate(
            String firstName,
            String otp
    ) {

        Context context = new Context();

        context.setVariable(
                "firstName",
                firstName
        );

        context.setVariable(
                "otp",
                otp
        );

        return templateEngine.process(
                "email/EmailOtpVerfication",
                context
        );
    }

    @Override
    public String getMobileVerificationTemplate(
            String firstName,
            String otp
    ) {

        Context context = new Context();

        context.setVariable(
                "firstName",
                firstName
        );

        context.setVariable(
                "otp",
                otp
        );

        return templateEngine.process(
                "email/MobileOtpVerification",
                context
        );
    }

    @Override
    public String getWelcomeTemplate(
            String firstName
    ) {

        Context context = new Context();

        context.setVariable(
                "firstName",
                firstName
        );

        return templateEngine.process(
                "email/WelcomeMail",
                context
        );
    }

    @Override
    public String getPasswordResetTemplate(
            String firstName,
            String resetLink
    ) {

        Context context = new Context();

        context.setVariable(
                "title",
                "Reset Your Password"
        );

        context.setVariable(
                "firstName",
                firstName
        );

        context.setVariable(
                "resetLink",
                resetLink
        );

        return templateEngine.process(
                "email/PasswordReset",
                context
        );
    }

    @Override
    public String getPasswordChangedSuccessTemplate(
            String name,
            LocalDateTime changedAt) {

        Context context = new Context();

        context.setVariable("name", name);
        context.setVariable("changedAt", changedAt);

        return templateEngine.process(
                "email/PasswordChangedSuccess",
                context
        );
    }

    @Override
    public String getPasswordResetSuccessTemplate(
            String name,
            LocalDateTime resetAt) {

        Context context = new Context();

        context.setVariable("name", name);
        context.setVariable("resetAt", resetAt);

        return templateEngine.process(
                "email/PasswordResetSuccess",
                context
        );
    }
}