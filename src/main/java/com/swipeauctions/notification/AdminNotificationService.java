package com.swipeauctions.notification;

import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Ops-facing email alerts (as opposed to {@link AuctionNotificationService}, which is user-facing).
 * No-ops quietly if {@code app.admin.alert-email} isn't configured — alerting is best-effort, never
 * something the platform depends on to function.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {

    private final EmailService emailService;

    @Value("${app.admin.alert-email:}")
    private String alertEmail;

    @Async
    public void paymentFailed(String userEmail, String purposeLabel, BigDecimal amount) {
        if (alertEmail == null || alertEmail.isBlank()) {
            log.debug("[admin-alert] no app.admin.alert-email configured — skipping payment-failed alert for {}", userEmail);
            return;
        }
        String subject = "[Alert] Payment failed — " + purposeLabel;
        String body = "<p>A user payment failed.</p>"
                + "<p><strong>User:</strong> " + esc(userEmail) + "</p>"
                + "<p><strong>Purpose:</strong> " + esc(purposeLabel) + "</p>"
                + "<p><strong>Amount:</strong> ₹" + (amount == null ? "—" : amount.stripTrailingZeros().toPlainString()) + "</p>";
        try {
            emailService.sendEmail(EmailRequestDTO.builder().to(alertEmail).subject(subject).body(body).build());
            log.info("[admin-alert] sent '{}' to {}", subject, alertEmail);
        } catch (Exception e) {
            log.warn("[admin-alert] failed to send '{}': {}", subject, e.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
