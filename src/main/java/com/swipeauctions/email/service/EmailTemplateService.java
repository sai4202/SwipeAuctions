package com.swipeauctions.email.service;

import java.time.LocalDateTime;

public interface EmailTemplateService {

    String getEmailVerificationTemplate(
            String firstName,
            String otp
    );

    String getMobileVerificationTemplate(
            String firstName,
            String otp
    );

    String getWelcomeTemplate(
            String firstName
    );

    String getPasswordResetTemplate(
            String firstName,
            String resetLink
    );

    String getPasswordChangedSuccessTemplate(
            String name,
            LocalDateTime changedAt
    );

    String getPasswordResetSuccessTemplate(
            String name,
            LocalDateTime resetAt
    );

}