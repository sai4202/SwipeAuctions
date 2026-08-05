package com.swipeauctions.notification;

import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Ops-facing payment-failure alerts: only fire when an admin inbox is actually configured, and
 *  never let an SMTP failure escape back to the caller (settle()). */
@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock private EmailService emailService;

    private AdminNotificationService adminNotificationService;

    @BeforeEach
    void setUp() {
        adminNotificationService = new AdminNotificationService(emailService);
    }

    @Test
    void alertEmailConfigured_sendsAlertToAdminInbox() {
        ReflectionTestUtils.setField(adminNotificationService, "alertEmail", "ops@swipeauctions.test");

        adminNotificationService.paymentFailed("user@example.com", "Subscription", new BigDecimal("1499"));

        org.mockito.ArgumentCaptor<EmailRequestDTO> captor = org.mockito.ArgumentCaptor.forClass(EmailRequestDTO.class);
        verify(emailService).sendEmail(captor.capture());
        EmailRequestDTO email = captor.getValue();
        assertThat(email.getTo()).isEqualTo("ops@swipeauctions.test");
        assertThat(email.getSubject()).contains("Payment failed");
        assertThat(email.getBody()).contains("user@example.com").contains("Subscription");
    }

    @Test
    void noAlertEmailConfigured_skipsSilently() {
        ReflectionTestUtils.setField(adminNotificationService, "alertEmail", "");

        adminNotificationService.paymentFailed("user@example.com", "Subscription", new BigDecimal("1499"));

        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void emailFailure_isSwallowedAndNeverPropagatesToCaller() {
        ReflectionTestUtils.setField(adminNotificationService, "alertEmail", "ops@swipeauctions.test");
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down")).when(emailService).sendEmail(any());

        adminNotificationService.paymentFailed("user@example.com", "Wallet top-up", new BigDecimal("500"));

        verify(emailService).sendEmail(any());
    }
}
