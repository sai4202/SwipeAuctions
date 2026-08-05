package com.swipeauctions.notification;

import com.swipeauctions.email.dto.EmailRequestDTO;
import com.swipeauctions.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sends user-facing notifications for auction/account events (login, register-to-bid, bid placed,
 * outbid, auction won/ended, wallet top-up) — both an email and a live in-app toast pushed over the
 * user's personal STOMP queue ({@code /user/queue/notifications}).
 *
 * <p>Every method is {@link Async} and swallows its own errors: a notification is a side effect, so
 * a slow/misconfigured SMTP server or a disconnected WebSocket must never fail a bid, a login, or
 * auction settlement. Email bodies are self-contained HTML (no external template files) styled to
 * match the brand emails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionNotificationService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    public void loginConfirmation(String email, LocalDateTime when, String device, String ip) {
        send(email, "New device sign-in to your SwipeAuctions account", wrap("New device sign-in",
                "<p>Your account was just signed in from a device we haven't seen before.</p>"
                        + row("When", TS.format(when))
                        + row("Device", device == null || device.isBlank() ? "Unknown device" : device)
                        + row("IP address", ip == null ? "—" : ip)
                        + "<p style=\"margin-top:18px\">If this wasn't you, reset your password and sign out other "
                        + "devices from your account settings right away.</p>"));
    }

    @Async
    public void registeredToBid(String email, String auctionTitle, BigDecimal holdAmount) {
        send(email, "You're registered to bid — " + auctionTitle, wrap("Registered to bid",
                "<p>You're all set to bid on <strong>" + esc(auctionTitle) + "</strong>.</p>"
                        + row("Refundable deposit held", money(holdAmount))
                        + "<p style=\"margin-top:18px\">The hold is released automatically if you don't win. "
                        + "Good luck!</p>"));
    }

    @Async
    public void bidPlaced(String email, String auctionId, String auctionTitle, BigDecimal amount, boolean isHighest) {
        send(email, "Bid confirmed — " + auctionTitle, wrap("Bid placed",
                "<p>Your bid on <strong>" + esc(auctionTitle) + "</strong> was placed successfully.</p>"
                        + row("Your bid", money(amount))
                        + row("Status", isHighest ? "You're the highest bidder 🎉" : "Placed")
                        + "<p style=\"margin-top:18px\">We'll email you if someone outbids you.</p>"));
        push(email, NotificationKind.BID_PLACED, "Bid placed",
                "Your bid of " + money(amount) + " on \"" + auctionTitle + "\" was placed.", auctionId);
    }

    @Async
    public void outbid(String email, String auctionId, String auctionTitle, BigDecimal newHighest) {
        send(email, "You've been outbid — " + auctionTitle, wrap("You've been outbid",
                "<p>Someone placed a higher bid on <strong>" + esc(auctionTitle) + "</strong>.</p>"
                        + row("Current highest bid", money(newHighest))
                        + "<p style=\"margin-top:18px\">Want to stay in the running? Head back to the auction and "
                        + "place a new bid before it closes.</p>"));
        push(email, NotificationKind.OUTBID, "You've been outbid",
                "Someone bid higher on \"" + auctionTitle + "\". Current highest: " + money(newHighest) + ".", auctionId);
    }

    @Async
    public void auctionWon(String email, String auctionId, String auctionTitle, BigDecimal amount) {
        send(email, "Congratulations — you won " + auctionTitle, wrap("You won! 🎊",
                "<p>You're the winning bidder for <strong>" + esc(auctionTitle) + "</strong>.</p>"
                        + row("Winning bid", money(amount))
                        + "<p style=\"margin-top:18px\">Your deposit has been applied. We'll follow up with payment "
                        + "and handover details.</p>"));
        push(email, NotificationKind.AUCTION_WON, "You won! 🎊",
                "You won \"" + auctionTitle + "\" for " + money(amount) + ".", auctionId);
    }

    @Async
    public void auctionEndedNotWon(String email, String auctionId, String auctionTitle, boolean sold, BigDecimal newCreditLimit) {
        send(email, "Auction ended — " + auctionTitle, wrap("Auction ended",
                "<p>Bidding has closed on <strong>" + esc(auctionTitle) + "</strong>.</p>"
                        + "<p>" + (sold ? "This time the winning bid was someone else's."
                                        : "The reserve price wasn't met, so the item didn't sell.")
                        + " Your refundable deposit has been released back to your wallet.</p>"
                        + row("Available credit limit now", moneyCompact(newCreditLimit))
                        + "<p style=\"margin-top:18px\">Plenty more lots are live — keep bidding!</p>"));
        push(email, NotificationKind.AUCTION_LOST, "Auction ended",
                "\"" + auctionTitle + "\" — " + (sold ? "won by someone else." : "reserve not met.")
                        + " Deposit released — credit limit is now " + moneyCompact(newCreditLimit) + ".", auctionId);
    }

    @Async
    public void walletTopUp(String email, BigDecimal amount) {
        send(email, "Wallet credited — " + money(amount), wrap("Wallet credited",
                "<p>Your wallet top-up was successful.</p>"
                        + row("Amount credited", money(amount))
                        + "<p style=\"margin-top:18px\">Funds are available to bid on right away.</p>"));
        push(email, NotificationKind.WALLET_TOPUP, "Wallet credited",
                money(amount) + " was added to your wallet.", null);
    }

    @Async
    public void referralBonusCredited(String email, BigDecimal amount) {
        send(email, "Referral bonus credited — " + money(amount), wrap("Referral bonus credited",
                "<p>Thanks for referring a friend — your bonus has been added to your wallet.</p>"
                        + row("Bonus credited", money(amount))
                        + "<p style=\"margin-top:18px\">Keep sharing your referral link to earn more.</p>"));
        push(email, NotificationKind.REFERRAL_BONUS, "Referral bonus credited",
                money(amount) + " was added to your wallet for a successful referral.", null);
    }

    @Async
    public void walletAdjusted(String email, BigDecimal amount, boolean credit, String reason) {
        send(email, credit ? "Wallet credited by admin" : "Wallet debited by admin",
                wrap(credit ? "Wallet credited by admin" : "Wallet debited by admin",
                        "<p>An admin has " + (credit ? "credited" : "debited") + " your wallet.</p>"
                                + row("Amount", money(amount))
                                + row("Reason", esc(reason == null || reason.isBlank() ? "—" : reason))
                                + "<p style=\"margin-top:18px\">Contact support if you have any questions about this adjustment.</p>"));
        push(email, NotificationKind.WALLET_ADJUSTED, credit ? "Wallet credited by admin" : "Wallet debited by admin",
                (credit ? money(amount) + " was added to" : money(amount) + " was deducted from")
                        + " your wallet by an admin. Reason: " + reason, null);
    }

    @Async
    public void registrationFeePaid(String email, BigDecimal amount) {
        send(email, "Registration fee received", wrap("Registration fee received",
                "<p>Your one-time registration fee has been received — your account is now fully activated.</p>"
                        + row("Amount paid", money(amount))
                        + "<p style=\"margin-top:18px\">You're all set to register for auctions and start bidding.</p>"));
        push(email, NotificationKind.REGISTRATION_FEE_PAID, "Registration fee received",
                money(amount) + " registration fee received. Your account is fully activated.", null);
    }

    @Async
    public void subscriptionActivated(String email, String tierLabel, String cycleLabel, LocalDateTime expiresAt) {
        send(email, "Subscription activated — " + tierLabel, wrap("Subscription activated",
                "<p>Your <strong>" + esc(tierLabel) + "</strong> subscription (" + esc(cycleLabel) + ") is now active.</p>"
                        + row("Renews / expires", expiresAt == null ? "—" : TS.format(expiresAt))
                        + "<p style=\"margin-top:18px\">Your new tier benefits are live on your account now.</p>"));
        push(email, NotificationKind.SUBSCRIPTION_ACTIVATED, "Subscription activated",
                tierLabel + " (" + cycleLabel + ") is now active on your account.", null);
    }

    @Async
    public void paymentFailed(String email, String purposeLabel, BigDecimal amount) {
        send(email, "Payment failed — " + purposeLabel, wrap("Payment failed",
                "<p>Your recent payment for <strong>" + esc(purposeLabel) + "</strong> could not be completed.</p>"
                        + row("Amount", money(amount))
                        + "<p style=\"margin-top:18px\">No funds were deducted. Please try again, or use a different "
                        + "payment method.</p>"));
        push(email, NotificationKind.PAYMENT_FAILED, "Payment failed",
                purposeLabel + " payment of " + money(amount) + " could not be completed.", null);
    }

    @Async
    public void settlementCompleted(String email, String auctionId, String auctionTitle, BigDecimal amount) {
        send(email, "Payment complete — " + auctionTitle, wrap("Payment complete",
                "<p>Your remaining payment for <strong>" + esc(auctionTitle) + "</strong> has been received in full.</p>"
                        + row("Amount paid", money(amount))
                        + "<p style=\"margin-top:18px\">We'll follow up shortly with handover details.</p>"));
        push(email, NotificationKind.SETTLEMENT_COMPLETED, "Payment complete",
                "Your payment of " + money(amount) + " for \"" + auctionTitle + "\" is complete.", auctionId);
    }

    @Async
    public void settlementPaymentReminder(String email, String auctionId, String auctionTitle, BigDecimal amountDue) {
        send(email, "Payment due — " + auctionTitle, wrap("Payment due",
                "<p>You won <strong>" + esc(auctionTitle) + "</strong>, but the remaining balance hasn't been paid yet.</p>"
                        + row("Amount due", money(amountDue))
                        + "<p style=\"margin-top:18px\">Top up your wallet and complete the payment from the auction "
                        + "page to finalize your win.</p>"));
        push(email, NotificationKind.SETTLEMENT_REMINDER, "Payment due",
                money(amountDue) + " is still due for \"" + auctionTitle + "\".", auctionId);
    }

    // ---- helpers ----

    private void send(String to, String subject, String body) {
        try {
            emailService.sendEmail(EmailRequestDTO.builder().to(to).subject(subject).body(body).build());
            log.info("[notify] sent '{}' to {}", subject, to);
        } catch (Exception e) {
            // Never let a notification failure surface to the caller's flow.
            log.warn("[notify] failed to send '{}' to {}: {}", subject, to, e.getMessage());
        }
    }

    /** Live in-app toast over the user's personal STOMP queue. No-ops quietly if they're not connected. */
    private void push(String email, NotificationKind kind, String title, String message, String auctionId) {
        try {
            messagingTemplate.convertAndSendToUser(email, "/queue/notifications",
                    PushNotification.of(kind, title, message, auctionId));
        } catch (Exception e) {
            log.debug("[notify] push failed for {}: {}", email, e.getMessage());
        }
    }

    private static String money(BigDecimal n) {
        return n == null ? "—" : "₹" + n.stripTrailingZeros().toPlainString();
    }

    private static final BigDecimal ONE_LAKH = new BigDecimal("100000");
    private static final BigDecimal ONE_CRORE = new BigDecimal("10000000");

    /** Same as {@link #money}, but collapses lakh/crore-scale amounts (the leveraged credit limit is
     *  always at least several lakh) into "₹2.50 Cr" / "₹4.50 L" instead of a wall of digits. */
    private static String moneyCompact(BigDecimal n) {
        if (n == null) return "—";
        if (n.compareTo(ONE_CRORE) >= 0) {
            return "₹" + n.divide(ONE_CRORE, 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " Cr";
        }
        if (n.compareTo(ONE_LAKH) >= 0) {
            return "₹" + n.divide(ONE_LAKH, 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " L";
        }
        return money(n);
    }

    private static String row(String label, String value) {
        return "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:6px 0\"><tr>"
                + "<td style=\"color:#64748B;font-size:14px\">" + esc(label) + "</td>"
                + "<td align=\"right\" style=\"color:#0F172A;font-weight:700;font-size:15px\">" + value + "</td>"
                + "</tr></table>";
    }

    /** Wraps content in the branded email shell (dark header + gold wordmark), fully self-contained. */
    private static String wrap(String heading, String contentHtml) {
        return "<div style=\"background:#F8FAFC;padding:40px 20px;font-family:Arial,Helvetica,sans-serif\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">"
                + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 10px 40px rgba(15,23,42,.12)\">"
                + "<tr><td align=\"center\" style=\"background:#0F172A;padding:32px\">"
                + "<h1 style=\"margin:0;color:#FBBF24;font-size:30px\">SWIPE AUCTIONS</h1>"
                + "<p style=\"color:#CBD5E1;margin-top:8px;font-size:13px\">Secure • Trusted • Transparent Auctions</p></td></tr>"
                + "<tr><td style=\"padding:36px;color:#334155;line-height:1.7;font-size:16px\">"
                + "<h2 style=\"margin:0 0 14px;color:#0F172A;font-size:22px\">" + esc(heading) + "</h2>"
                + contentHtml + "</td></tr>"
                + "<tr><td style=\"background:#F8FAFC;padding:24px;text-align:center;color:#64748B;font-size:12px\">"
                + "<p style=\"margin:4px 0\">This is an automated message from Swipe Auctions.</p>"
                + "<p style=\"margin:4px 0\">© 2026 Swipe Auctions. All Rights Reserved.</p></td></tr>"
                + "</table></td></tr></table></div>";
    }

    /** Minimal HTML-escape for user-supplied strings (listing titles). */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
