package com.swipeauctions.payment;

import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.notification.AdminNotificationService;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.referral.service.ReferralService;
import com.swipeauctions.settings.repository.MembershipBenefitRepository;
import com.swipeauctions.settings.repository.SubscriptionPlanPriceRepository;
import com.swipeauctions.settings.service.PlatformSettingsService;
import com.swipeauctions.settings.service.SubscriptionService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.wallet.entity.PaymentOrder;
import com.swipeauctions.wallet.enums.PaymentPurpose;
import com.swipeauctions.wallet.enums.TopUpStatus;
import com.swipeauctions.wallet.repository.PaymentOrderRepository;
import com.swipeauctions.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of every notification wired into payment settlement: registration-fee
 * confirmation, subscription activation, and the payment-failed user email + admin alert — plus the
 * pre-existing wallet top-up path and idempotency against a double settle (client-verify racing the
 * webhook). Pure Mockito — settle() is package-private specifically so this test can drive it
 * directly without going through Razorpay's static signature-verification calls.
 */
@ExtendWith(MockitoExtension.class)
class RazorpayPaymentServiceSettleTest {

    @Mock private RazorpayConfig config;
    @Mock private UserRepository userRepository;
    @Mock private PaymentOrderRepository orderRepository;
    @Mock private WalletService walletService;
    @Mock private ReferralService referralService;
    @Mock private PlatformSettingsService platformSettingsService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private SubscriptionPlanPriceRepository priceRepository;
    @Mock private MembershipBenefitRepository membershipBenefitRepository;
    @Mock private AuctionNotificationService notificationService;
    @Mock private AdminNotificationService adminNotificationService;

    private RazorpayPaymentService service;

    @BeforeEach
    void setUp() {
        service = new RazorpayPaymentService(config, userRepository, orderRepository, walletService,
                referralService, platformSettingsService, subscriptionService, priceRepository,
                membershipBenefitRepository, notificationService, adminNotificationService);
    }

    private static User user(String email) {
        User u = User.builder().email(email).build();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static PaymentOrder order(User u, PaymentPurpose purpose, BigDecimal amount, String metadata) {
        return PaymentOrder.builder().user(u).purpose(purpose).amount(amount)
                .metadata(metadata).status(TopUpStatus.PENDING).razorpayOrderId("order_" + UUID.randomUUID())
                .build();
    }

    @Test
    void walletTopUp_success_creditsWalletAndAppliesReferral_noAdminAlert() {
        User u = user("bidder@example.com");
        PaymentOrder o = order(u, PaymentPurpose.WALLET_TOPUP, new BigDecimal("1000"), null);

        service.settle(o, TopUpStatus.SUCCEEDED);

        verify(walletService).topUp(u, new BigDecimal("1000"));
        verify(referralService).onTopUp(u, new BigDecimal("1000"));
        verify(orderRepository).save(o);
        assertThat(o.getStatus()).isEqualTo(TopUpStatus.SUCCEEDED);
        verify(notificationService, never()).paymentFailed(any(), any(), any());
        verify(adminNotificationService, never()).paymentFailed(any(), any(), any());
    }

    @Test
    void registrationFee_success_flipsFlagAndEmailsConfirmation() {
        User u = user("buyer@example.com");
        PaymentOrder o = order(u, PaymentPurpose.REGISTRATION_FEE, new BigDecimal("999"), null);

        service.settle(o, TopUpStatus.SUCCEEDED);

        assertThat(u.getRegistrationFeePaid()).isTrue();
        assertThat(u.getRegistrationFeePaidAt()).isNotNull();
        verify(userRepository).save(u);
        verify(notificationService).registrationFeePaid("buyer@example.com", new BigDecimal("999"));
    }

    @Test
    void subscription_success_grantsTierAndEmailsActivation() {
        User u = user("subscriber@example.com");
        User updated = user("subscriber@example.com");
        updated.setSubscriptionExpiresAt(java.time.LocalDateTime.now().plusMonths(1));
        PaymentOrder o = order(u, PaymentPurpose.SUBSCRIPTION, new BigDecimal("2499"), "GOLD:MONTHLY");

        when(subscriptionService.subscribe(u, SubscriptionTier.GOLD, BillingCycle.MONTHLY)).thenReturn(updated);

        service.settle(o, TopUpStatus.SUCCEEDED);

        verify(subscriptionService).subscribe(u, SubscriptionTier.GOLD, BillingCycle.MONTHLY);
        verify(notificationService).subscriptionActivated(eq("subscriber@example.com"), eq("GOLD"), eq("MONTHLY"),
                eq(updated.getSubscriptionExpiresAt()));
    }

    @Test
    void registrationFee_failed_emailsUserAndAlertsAdmin_grantsNothing() {
        User u = user("buyer@example.com");
        PaymentOrder o = order(u, PaymentPurpose.REGISTRATION_FEE, new BigDecimal("999"), null);

        service.settle(o, TopUpStatus.FAILED);

        assertThat(o.getStatus()).isEqualTo(TopUpStatus.FAILED);
        assertThat(u.getRegistrationFeePaid()).isFalse();
        verify(userRepository, never()).save(any());
        verify(notificationService).paymentFailed("buyer@example.com", "Registration fee", new BigDecimal("999"));
        verify(adminNotificationService).paymentFailed("buyer@example.com", "Registration fee", new BigDecimal("999"));
    }

    @Test
    void subscription_failed_emailsUserAndAlertsAdmin_grantsNothing() {
        User u = user("subscriber@example.com");
        PaymentOrder o = order(u, PaymentPurpose.SUBSCRIPTION, new BigDecimal("2499"), "GOLD:MONTHLY");

        service.settle(o, TopUpStatus.FAILED);

        verify(subscriptionService, never()).subscribe(any(), any(), any());
        verify(notificationService).paymentFailed("subscriber@example.com", "Subscription", new BigDecimal("2499"));
        verify(adminNotificationService).paymentFailed("subscriber@example.com", "Subscription", new BigDecimal("2499"));
    }

    @Test
    void walletTopUp_failed_emailsUserAndAlertsAdmin_creditsNothing() {
        User u = user("bidder@example.com");
        PaymentOrder o = order(u, PaymentPurpose.WALLET_TOPUP, new BigDecimal("1000"), null);

        service.settle(o, TopUpStatus.FAILED);

        verify(walletService, never()).topUp(any(), any());
        verify(notificationService).paymentFailed("bidder@example.com", "Wallet top-up", new BigDecimal("1000"));
        verify(adminNotificationService).paymentFailed("bidder@example.com", "Wallet top-up", new BigDecimal("1000"));
    }

    @Test
    void alreadySettledOrder_isIdempotent_noDoubleGrantAndNoDoubleNotification() {
        User u = user("buyer@example.com");
        PaymentOrder o = order(u, PaymentPurpose.REGISTRATION_FEE, new BigDecimal("999"), null);
        o.setStatus(TopUpStatus.SUCCEEDED); // client-verify already settled it before the webhook arrives

        service.settle(o, TopUpStatus.SUCCEEDED);

        verify(orderRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(notificationService, never()).registrationFeePaid(any(), any());
        verify(notificationService, never()).paymentFailed(any(), any(), any());
    }
}
