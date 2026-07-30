package com.swipeauctions.payment;

import com.razorpay.FundAccount;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.swipeauctions.common.exception.BadRequestException;
import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.settings.entity.SubscriptionPlanPrice;
import com.swipeauctions.settings.repository.SubscriptionPlanPriceRepository;
import com.swipeauctions.settings.service.PlatformSettingsService;
import com.swipeauctions.settings.service.SubscriptionService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.wallet.entity.PaymentOrder;
import com.swipeauctions.wallet.entity.WalletWithdrawal;
import com.swipeauctions.wallet.enums.PaymentPurpose;
import com.swipeauctions.wallet.enums.TopUpStatus;
import com.swipeauctions.wallet.repository.PaymentOrderRepository;
import com.swipeauctions.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Everything that actually talks to Razorpay: order creation + payment verification for the three
 * buyer-paid flows (wallet top-up, registration fee, subscription — see {@link PaymentPurpose}),
 * webhook confirmation, and seller payouts via RazorpayX. All amounts are INR, converted to the
 * smallest unit (paise) at the SDK boundary since Razorpay amounts are always integer minor units.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayPaymentService {

    private static final String CURRENCY = "INR";

    private final RazorpayConfig config;
    private final UserRepository userRepository;
    private final PaymentOrderRepository orderRepository;
    private final WalletService walletService;
    private final PlatformSettingsService platformSettingsService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionPlanPriceRepository priceRepository;

    private void requireEnabled() {
        if (!config.isEnabled()) {
            throw new BadRequestException(
                    "Razorpay is not configured yet. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET to enable real payments.");
        }
    }

    private RazorpayClient client() {
        try {
            return new RazorpayClient(config.getKeyId(), config.getKeySecret());
        } catch (RazorpayException e) {
            throw new BadRequestException("Could not reach Razorpay: " + e.getMessage());
        }
    }

    public record OrderIntent(String orderId, long amountPaise, String currency, String keyId) {}

    // ---- Order creation — one generic path, reused by top-up / registration fee / subscription ----

    @Transactional
    public OrderIntent createOrder(User user, BigDecimal amount, PaymentPurpose purpose, String metadata) {
        requireEnabled();
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
        try {
            long paise = toMinorUnits(amount);
            JSONObject notes = new JSONObject();
            notes.put("userId", user.getId().toString());
            notes.put("purpose", purpose.name());

            JSONObject req = new JSONObject();
            req.put("amount", paise);
            req.put("currency", CURRENCY);
            // Razorpay caps receipt at 40 chars; full user id + purpose already travel in notes.
            String shortUserId = user.getId().toString().replace("-", "").substring(0, 12);
            req.put("receipt", purpose.name().substring(0, Math.min(purpose.name().length(), 12))
                    + "-" + shortUserId + "-" + System.currentTimeMillis() % 100000);
            req.put("notes", notes);

            Order order = client().orders.create(req);
            String orderId = order.get("id");

            orderRepository.save(PaymentOrder.builder()
                    .user(user).razorpayOrderId(orderId).amount(amount)
                    .purpose(purpose).metadata(metadata).status(TopUpStatus.PENDING).build());

            return new OrderIntent(orderId, paise, CURRENCY, config.getKeyId());
        } catch (RazorpayException e) {
            log.error("[razorpay] order creation failed for user {} purpose {}", user.getId(), purpose, e);
            throw new BadRequestException("Could not start payment: " + e.getMessage());
        }
    }

    @Transactional
    public OrderIntent createTopUpOrder(User user, BigDecimal amount) {
        return createOrder(user, amount, PaymentPurpose.WALLET_TOPUP, null);
    }

    @Transactional
    public OrderIntent createRegistrationFeeOrder(User user) {
        if (Boolean.TRUE.equals(user.getRegistrationFeePaid())) {
            throw new BadRequestException("Registration fee already paid");
        }
        BigDecimal fee = platformSettingsService.getRegistrationFee();
        return createOrder(user, fee, PaymentPurpose.REGISTRATION_FEE, null);
    }

    @Transactional
    public OrderIntent createSubscriptionOrder(User user, SubscriptionTier tier, BillingCycle cycle) {
        SubscriptionPlanPrice priceRow = priceRepository.findByTierAndBillingCycle(tier, cycle)
                .orElseThrow(() -> new BadRequestException("No price configured for " + tier + "/" + cycle));
        return createOrder(user, priceRow.getPrice(), PaymentPurpose.SUBSCRIPTION, tier.name() + ":" + cycle.name());
    }

    // ---- Verification — the client-side fast path, right after Razorpay Checkout succeeds ----

    @Transactional
    public void verifyAndSettle(User user, String orderId, String paymentId, String signature) {
        requireEnabled();
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", orderId);
        options.put("razorpay_payment_id", paymentId);
        options.put("razorpay_signature", signature);
        boolean valid;
        try {
            valid = Utils.verifyPaymentSignature(options, config.getKeySecret());
        } catch (RazorpayException e) {
            throw new BadRequestException("Could not verify payment: " + e.getMessage());
        }
        if (!valid) {
            throw new BadRequestException("Payment signature verification failed");
        }
        PaymentOrder order = orderRepository.findByRazorpayOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BadRequestException("Unknown order"));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("This order does not belong to you");
        }
        settle(order, TopUpStatus.SUCCEEDED);
    }

    // ---- Webhook — the authoritative confirmation; idempotent against Razorpay's retries ----

    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        requireEnabled();
        try {
            Utils.verifyWebhookSignature(payload, signatureHeader, config.getWebhookSecret());
        } catch (RazorpayException e) {
            throw new BadRequestException("Invalid Razorpay webhook signature");
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.optString("event");
        JSONObject paymentPayload = event.optJSONObject("payload");
        JSONObject paymentEntity = paymentPayload != null ? paymentPayload.optJSONObject("payment") : null;
        JSONObject entity = paymentEntity != null ? paymentEntity.optJSONObject("entity") : null;
        String orderId = entity != null ? entity.optString("order_id", null) : null;
        if (orderId == null) {
            log.debug("[razorpay] ignoring webhook event type {} (no order id)", eventType);
            return;
        }

        switch (eventType) {
            case "payment.captured" -> settleByOrderId(orderId, TopUpStatus.SUCCEEDED);
            case "payment.failed" -> settleByOrderId(orderId, TopUpStatus.FAILED);
            default -> log.debug("[razorpay] ignoring webhook event type {}", eventType);
        }
    }

    private void settleByOrderId(String orderId, TopUpStatus outcome) {
        PaymentOrder order = orderRepository.findByRazorpayOrderIdForUpdate(orderId).orElse(null);
        if (order == null) {
            log.warn("[razorpay] webhook for unknown order {}", orderId);
            return;
        }
        settle(order, outcome);
    }

    /** Idempotent — only acts if the order is still PENDING, so the client-verify call and the
     *  webhook (or a redelivered webhook) racing each other settle exactly once. */
    private void settle(PaymentOrder order, TopUpStatus outcome) {
        if (order.getStatus() != TopUpStatus.PENDING) {
            return;
        }
        order.setStatus(outcome);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        if (outcome != TopUpStatus.SUCCEEDED) {
            return;
        }
        User user = order.getUser();
        switch (order.getPurpose()) {
            case WALLET_TOPUP -> walletService.topUp(user, order.getAmount());
            case REGISTRATION_FEE -> {
                user.setRegistrationFeePaid(true);
                user.setRegistrationFeePaidAt(LocalDateTime.now());
                userRepository.save(user);
            }
            case SUBSCRIPTION -> {
                String[] parts = order.getMetadata().split(":");
                subscriptionService.subscribe(user, SubscriptionTier.valueOf(parts[0]), BillingCycle.valueOf(parts[1]));
            }
        }
    }

    // ---- Seller payouts (RazorpayX) ----
    // No OAuth-redirect onboarding like Stripe Connect — a seller submits their bank details
    // directly in-app; we turn that into a Razorpay Contact + Fund Account server-side. Gated
    // independently of createOrder/verify (which only need standard Payments keys) since Payouts
    // is a separate RazorpayX product requiring its own account/virtual account number.
    //
    // Contacts and Payouts aren't wrapped by this SDK version (razorpay-java only ships typed
    // clients for standard Payments — no Contact/Payout classes), so those two calls go straight
    // to Razorpay's REST API with the same key-id:key-secret Basic Auth the SDK itself uses.
    // Fund Account creation IS covered by the SDK (FundAccountClient), so that one call still goes
    // through client().fundAccount as usual.

    private static final String API_BASE = "https://api.razorpay.com/v1/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private void requirePayoutsEnabled() {
        if (!config.payoutsEnabled()) {
            throw new BadRequestException(
                    "Payouts are not configured yet. Set RAZORPAY_ACCOUNT_NUMBER (RazorpayX) to enable withdrawals.");
        }
    }

    /** Raw POST against the Razorpay REST API for endpoints this SDK version doesn't wrap. */
    private JSONObject rawApiPost(String path, JSONObject body) {
        String credentials = config.getKeyId() + ":" + config.getKeySecret();
        String auth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + path))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            if (response.statusCode() >= 300) {
                JSONObject error = json.optJSONObject("error");
                String message = error != null ? error.optString("description", "Razorpay API error") : "Razorpay API error";
                throw new BadRequestException(message);
            }
            return json;
        } catch (java.io.IOException e) {
            throw new BadRequestException("Could not reach Razorpay: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Payout request interrupted");
        }
    }

    @Transactional
    public User ensurePayoutAccount(User seller, String accountHolderName, String accountNumber, String ifsc) {
        requirePayoutsEnabled();
        try {
            String contactId = seller.getRazorpayContactId();
            if (contactId == null) {
                JSONObject contactReq = new JSONObject();
                contactReq.put("name", accountHolderName);
                contactReq.put("email", seller.getEmail());
                contactReq.put("contact", seller.getMobileNumber());
                contactReq.put("type", "vendor");
                contactId = rawApiPost("contacts", contactReq).getString("id");
                seller.setRazorpayContactId(contactId);
            }

            JSONObject bankAccount = new JSONObject();
            bankAccount.put("name", accountHolderName);
            bankAccount.put("account_number", accountNumber);
            bankAccount.put("ifsc", ifsc);
            JSONObject fundReq = new JSONObject();
            fundReq.put("contact_id", contactId);
            fundReq.put("account_type", "bank_account");
            fundReq.put("bank_account", bankAccount);
            FundAccount fundAccount = client().fundAccount.create(fundReq);

            seller.setRazorpayFundAccountId(fundAccount.get("id"));
            seller.setRazorpayPayoutsEnabled(true);
            return userRepository.save(seller);
        } catch (RazorpayException e) {
            log.error("[razorpay] payout account setup failed for user {}", seller.getId(), e);
            throw new BadRequestException("Could not set up payout account: " + e.getMessage());
        }
    }

    /**
     * Full withdrawal: debits the wallet immediately (via {@link WalletService#initiateWithdrawal}),
     * then pays out via a Razorpay Payout. If the Payout call fails, the debit is reversed so the
     * seller isn't left short.
     */
    @Transactional
    public WalletWithdrawal withdraw(User seller, BigDecimal amount) {
        requirePayoutsEnabled();
        if (!Boolean.TRUE.equals(seller.getRazorpayPayoutsEnabled()) || seller.getRazorpayFundAccountId() == null) {
            throw new BadRequestException("Add your payout bank account before withdrawing.");
        }
        WalletWithdrawal withdrawal = walletService.initiateWithdrawal(seller, amount);
        try {
            JSONObject req = new JSONObject();
            req.put("account_number", config.getAccountNumber());
            req.put("fund_account_id", seller.getRazorpayFundAccountId());
            req.put("amount", toMinorUnits(amount));
            req.put("currency", CURRENCY);
            req.put("mode", "IMPS");
            req.put("purpose", "payout");
            req.put("queue_if_low_balance", true);
            String payoutId = rawApiPost("payouts", req).getString("id");
            walletService.markWithdrawalSucceeded(withdrawal, payoutId);
            return withdrawal;
        } catch (BadRequestException e) {
            log.error("[razorpay] payout failed for user {}: {}", seller.getId(), e.getMessage());
            walletService.markWithdrawalFailed(withdrawal);
            throw new BadRequestException("Payout failed, your balance has been restored: " + e.getMessage());
        }
    }

    /** INR has 2 decimal places — Razorpay amounts are always integer minor units (paise). */
    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}
