package com.swipeauctions.payment;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.Transfer;
import com.stripe.net.Webhook;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.TransferCreateParams;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.entity.WalletTopUp;
import com.swipeauctions.wallet.entity.WalletWithdrawal;
import com.swipeauctions.wallet.enums.TopUpStatus;
import com.swipeauctions.wallet.repository.WalletTopUpRepository;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Everything that actually talks to Stripe: buyer wallet top-up (PaymentIntent + webhook credit),
 * seller Connect Express onboarding, and payout Transfers. All amounts are INR, converted to the
 * smallest unit (paise) at the SDK boundary since Stripe amounts are always integer minor units.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentService {

    private static final String CURRENCY = "inr";

    private final StripeConfig stripeConfig;
    private final UserRepository userRepository;
    private final WalletTopUpRepository topUpRepository;
    private final WalletService walletService;

    private void requireEnabled() {
        if (!stripeConfig.isEnabled()) {
            throw new BadRequestException(
                    "Stripe is not configured yet. Set STRIPE_SECRET_KEY (and STRIPE_PUBLISHABLE_KEY) to enable real payments.");
        }
    }

    public record TopUpIntent(String clientSecret, String publishableKey) {}

    @Transactional
    public TopUpIntent createTopUpIntent(User user, BigDecimal amount) {
        requireEnabled();
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
        try {
            String customerId = ensureCustomer(user);
            Wallet wallet = walletService.getOrCreateWallet(user);

            PaymentIntent intent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                    .setAmount(toMinorUnits(amount))
                    .setCurrency(CURRENCY)
                    .setCustomer(customerId)
                    .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true).build())
                    .putMetadata("userId", user.getId().toString())
                    .build());

            topUpRepository.save(WalletTopUp.builder()
                    .wallet(wallet)
                    .stripePaymentIntentId(intent.getId())
                    .amount(amount)
                    .status(TopUpStatus.PENDING)
                    .build());

            return new TopUpIntent(intent.getClientSecret(), stripeConfig.getPublishableKey());
        } catch (StripeException e) {
            log.error("[stripe] top-up intent creation failed for user {}", user.getId(), e);
            throw new BadRequestException("Could not start payment: " + e.getMessage());
        }
    }

    /** Verifies and dispatches a Stripe webhook payload. Idempotent — safe on Stripe's retries. */
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        requireEnabled();
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        }

        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                if (obj instanceof PaymentIntent pi) creditTopUp(pi, TopUpStatus.SUCCEEDED);
            }
            case "payment_intent.payment_failed" -> {
                if (obj instanceof PaymentIntent pi) creditTopUp(pi, TopUpStatus.FAILED);
            }
            case "account.updated" -> {
                if (obj instanceof Account acc) syncConnectStatus(acc);
            }
            default -> log.debug("[stripe] ignoring webhook event type {}", event.getType());
        }
    }

    private void creditTopUp(PaymentIntent pi, TopUpStatus outcome) {
        // Locks the row so a duplicate/retried webhook delivery for the same PaymentIntent can't read
        // PENDING twice before either transaction commits its status update (see repository javadoc).
        WalletTopUp topUp = topUpRepository.findByStripePaymentIntentIdForUpdate(pi.getId()).orElse(null);
        if (topUp == null) {
            log.warn("[stripe] webhook for unknown PaymentIntent {}", pi.getId());
            return;
        }
        if (topUp.getStatus() != TopUpStatus.PENDING) {
            return; // already processed — Stripe retries webhooks, this keeps it idempotent
        }
        topUp.setStatus(outcome);
        topUp.setCompletedAt(LocalDateTime.now());
        topUpRepository.save(topUp);
        if (outcome == TopUpStatus.SUCCEEDED) {
            walletService.topUp(topUp.getWallet().getUser(), topUp.getAmount());
        }
    }

    // ---- Seller Connect ----

    @Transactional
    public String createOnboardingLink(User seller) {
        requireEnabled();
        try {
            String accountId = seller.getStripeAccountId();
            if (accountId == null) {
                Account account = Account.create(AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.EXPRESS)
                        .setEmail(seller.getEmail())
                        .setCapabilities(AccountCreateParams.Capabilities.builder()
                                .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                                .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder().setRequested(true).build())
                                .build())
                        .build());
                accountId = account.getId();
                seller.setStripeAccountId(accountId);
                userRepository.save(seller);
            }
            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl(stripeConfig.getConnectRefreshUrl())
                    .setReturnUrl(stripeConfig.getConnectReturnUrl())
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());
            return link.getUrl();
        } catch (StripeException e) {
            log.error("[stripe] Connect onboarding link failed for user {}", seller.getId(), e);
            throw new BadRequestException("Could not start payout onboarding: " + e.getMessage());
        }
    }

    /** Re-checks charges/payouts-enabled with Stripe (call after the onboarding redirect returns). */
    @Transactional
    public User refreshConnectStatus(User seller) {
        requireEnabled();
        if (seller.getStripeAccountId() == null) {
            throw new BadRequestException("No Stripe payout account yet — start onboarding first.");
        }
        try {
            Account account = Account.retrieve(seller.getStripeAccountId());
            syncConnectStatus(account);
            return userRepository.findById(seller.getId()).orElse(seller);
        } catch (StripeException e) {
            throw new BadRequestException("Could not check payout status: " + e.getMessage());
        }
    }

    private void syncConnectStatus(Account account) {
        userRepository.findByStripeAccountId(account.getId()).ifPresent(u -> {
            u.setStripeChargesEnabled(Boolean.TRUE.equals(account.getChargesEnabled()));
            u.setStripePayoutsEnabled(Boolean.TRUE.equals(account.getPayoutsEnabled()));
            userRepository.save(u);
        });
    }

    /**
     * Full withdrawal: debits the wallet immediately (via {@link WalletService#initiateWithdrawal}),
     * then moves platform balance to the seller's connected account via a Stripe Transfer. If the
     * Transfer call fails, the debit is reversed so the seller isn't left short.
     */
    @Transactional
    public WalletWithdrawal withdraw(User seller, BigDecimal amount) {
        requireEnabled();
        if (!Boolean.TRUE.equals(seller.getStripePayoutsEnabled()) || seller.getStripeAccountId() == null) {
            throw new BadRequestException("Complete Stripe payout onboarding before withdrawing.");
        }
        WalletWithdrawal withdrawal = walletService.initiateWithdrawal(seller, amount);
        try {
            Transfer transfer = Transfer.create(TransferCreateParams.builder()
                    .setAmount(toMinorUnits(amount))
                    .setCurrency(CURRENCY)
                    .setDestination(seller.getStripeAccountId())
                    .setDescription("SwipeAuctions wallet withdrawal")
                    .build());
            walletService.markWithdrawalSucceeded(withdrawal, transfer.getId());
            return withdrawal;
        } catch (StripeException e) {
            log.error("[stripe] transfer failed for user {}", seller.getId(), e);
            walletService.markWithdrawalFailed(withdrawal);
            throw new BadRequestException("Payout failed, your balance has been restored: " + e.getMessage());
        }
    }

    private String ensureCustomer(User user) {
        if (user.getStripeCustomerId() != null) {
            return user.getStripeCustomerId();
        }
        try {
            Customer customer = Customer.create(CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .putMetadata("userId", user.getId().toString())
                    .build());
            user.setStripeCustomerId(customer.getId());
            userRepository.save(user);
            return customer.getId();
        } catch (StripeException e) {
            throw new BadRequestException("Could not set up billing profile: " + e.getMessage());
        }
    }

    /** INR has 2 decimal places — Stripe amounts are always integer minor units (paise). */
    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}
