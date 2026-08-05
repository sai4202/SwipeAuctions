package com.swipeauctions.notification;

import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Every user-facing notification scenario: login, bid lifecycle, auction win/loss, wallet/credit
 * events, registration-fee payment, subscription activation, payment failure, and settlement
 * payoff/reminder. Each one must (a) actually call the email sender with the right recipient, and
 * (b) never let an email-send failure propagate back to the caller.
 */
@ExtendWith(MockitoExtension.class)
class AuctionNotificationServiceTest {

    @Mock private EmailService emailService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private AuctionNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new AuctionNotificationService(emailService, messagingTemplate);
    }

    private EmailRequestDTO capturedEmail() {
        ArgumentCaptor<EmailRequestDTO> captor = ArgumentCaptor.forClass(EmailRequestDTO.class);
        verify(emailService).sendEmail(captor.capture());
        return captor.getValue();
    }

    @Test
    void loginConfirmation_emailsTheAccountHolder() {
        notificationService.loginConfirmation("user@example.com", LocalDateTime.now(), "Chrome on Windows", "1.2.3.4");

        EmailRequestDTO email = capturedEmail();
        assertThat(email.getTo()).isEqualTo("user@example.com");
        assertThat(email.getSubject()).contains("New device sign-in");
    }

    @Test
    void bidPlaced_sendsBothEmailAndPush() {
        notificationService.bidPlaced("bidder@example.com", "auction-1", "1965 Mustang", new BigDecimal("5000"), true);

        assertThat(capturedEmail().getTo()).isEqualTo("bidder@example.com");
        verify(messagingTemplate).convertAndSendToUser(eq("bidder@example.com"), eq("/queue/notifications"), any());
    }

    @Test
    void outbid_sendsBothEmailAndPush() {
        notificationService.outbid("bidder@example.com", "auction-1", "1965 Mustang", new BigDecimal("6000"));

        assertThat(capturedEmail().getSubject()).contains("outbid");
        verify(messagingTemplate).convertAndSendToUser(eq("bidder@example.com"), anyString(), any());
    }

    @Test
    void auctionWon_sendsBothEmailAndPush() {
        notificationService.auctionWon("winner@example.com", "auction-1", "1965 Mustang", new BigDecimal("10000"));

        assertThat(capturedEmail().getSubject()).contains("won");
        verify(messagingTemplate).convertAndSendToUser(eq("winner@example.com"), anyString(), any());
    }

    @Test
    void auctionEndedNotWon_sendsBothEmailAndPush() {
        notificationService.auctionEndedNotWon("loser@example.com", "auction-1", "1965 Mustang", true, new BigDecimal("250000"));

        assertThat(capturedEmail().getTo()).isEqualTo("loser@example.com");
        verify(messagingTemplate).convertAndSendToUser(eq("loser@example.com"), anyString(), any());
    }

    @Test
    void walletTopUp_nowSendsEmailInAdditionToPush() {
        notificationService.walletTopUp("user@example.com", new BigDecimal("1000"));

        assertThat(capturedEmail().getTo()).isEqualTo("user@example.com");
        assertThat(capturedEmail().getSubject()).contains("Wallet credited");
        verify(messagingTemplate).convertAndSendToUser(eq("user@example.com"), anyString(), any());
    }

    @Test
    void referralBonusCredited_nowSendsEmailInAdditionToPush() {
        notificationService.referralBonusCredited("user@example.com", new BigDecimal("500"));

        assertThat(capturedEmail().getSubject()).contains("Referral bonus");
        verify(messagingTemplate).convertAndSendToUser(eq("user@example.com"), anyString(), any());
    }

    @Test
    void walletAdjustedCredit_nowSendsEmailInAdditionToPush() {
        notificationService.walletAdjusted("user@example.com", new BigDecimal("200"), true, "Goodwill credit");

        EmailRequestDTO email = capturedEmail();
        assertThat(email.getSubject()).contains("credited by admin");
        assertThat(email.getBody()).contains("Goodwill credit");
    }

    @Test
    void walletAdjustedDebit_nowSendsEmailInAdditionToPush() {
        notificationService.walletAdjusted("user@example.com", new BigDecimal("200"), false, "Chargeback");

        assertThat(capturedEmail().getSubject()).contains("debited by admin");
    }

    @Test
    void registrationFeePaid_emailsConfirmation() {
        notificationService.registrationFeePaid("user@example.com", new BigDecimal("999"));

        EmailRequestDTO email = capturedEmail();
        assertThat(email.getTo()).isEqualTo("user@example.com");
        assertThat(email.getSubject()).contains("Registration fee");
        verify(messagingTemplate).convertAndSendToUser(eq("user@example.com"), anyString(), any());
    }

    @Test
    void subscriptionActivated_emailsConfirmationWithTierAndExpiry() {
        LocalDateTime expiry = LocalDateTime.now().plusMonths(1);

        notificationService.subscriptionActivated("user@example.com", "GOLD", "MONTHLY", expiry);

        EmailRequestDTO email = capturedEmail();
        assertThat(email.getSubject()).contains("Subscription activated").contains("GOLD");
        assertThat(email.getBody()).contains("MONTHLY");
    }

    @Test
    void paymentFailed_emailsTheUserWithNoAmountDeductedReassurance() {
        notificationService.paymentFailed("user@example.com", "Subscription", new BigDecimal("1499"));

        EmailRequestDTO email = capturedEmail();
        assertThat(email.getSubject()).contains("Payment failed");
        assertThat(email.getBody()).contains("No funds were deducted");
    }

    @Test
    void settlementCompleted_emailsTheWinner() {
        notificationService.settlementCompleted("winner@example.com", "auction-1", "1965 Mustang", new BigDecimal("2000"));

        EmailRequestDTO email = capturedEmail();
        assertThat(email.getSubject()).contains("Payment complete");
        verify(messagingTemplate).convertAndSendToUser(eq("winner@example.com"), anyString(), any());
    }

    @Test
    void settlementPaymentReminder_emailsTheWinnerWithAmountDue() {
        notificationService.settlementPaymentReminder("winner@example.com", "auction-1", "1965 Mustang", new BigDecimal("3500"));

        EmailRequestDTO email = capturedEmail();
        assertThat(email.getSubject()).contains("Payment due");
        assertThat(email.getBody()).contains("3500");
    }

    @Test
    void emailFailure_isSwallowedAndNeverPropagatesToCaller() {
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP down")).when(emailService).sendEmail(any());

        notificationService.loginConfirmation("user@example.com", LocalDateTime.now(), "Chrome", "1.2.3.4");

        verify(emailService, times(1)).sendEmail(any());
    }

    @Test
    void pushFailure_isSwallowedAndNeverPropagatesToCaller() {
        org.mockito.Mockito.doThrow(new RuntimeException("socket closed"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        notificationService.bidPlaced("bidder@example.com", "auction-1", "Lot", new BigDecimal("100"), false);

        verify(emailService).sendEmail(any());
    }

    @Test
    void walletTopUp_stillSendsEmailEvenWhenPushFails() {
        org.mockito.Mockito.doThrow(new RuntimeException("socket closed"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        notificationService.walletTopUp("user@example.com", new BigDecimal("1000"));

        verify(emailService, times(1)).sendEmail(any());
    }
}
