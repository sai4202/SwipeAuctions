package com.swipeauctions.notification;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.user.entity.User;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Renders the winner's settlement confirmation letter — a one-page PDF receipt — once an auction is
 * fully paid. Built from a small hand-written XHTML string (same self-contained-HTML convention as
 * {@link AuctionNotificationService}'s emails) and converted to PDF bytes via openhtmltopdf, which
 * requires well-formed XHTML input (every tag closed/self-closed) rather than loose HTML.
 */
@Service
public class ConfirmationLetterService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    public byte[] generate(Auction a) {
        User winner = a.getCurrentWinner();
        String html = buildHtml(a, winner);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate confirmation letter", e);
        }
    }

    private String buildHtml(Auction a, User winner) {
        BigDecimal amountPaid = a.getCurrentHighestBid();
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                + "<head><style>"
                + "body { font-family: Helvetica, Arial, sans-serif; color: #334155; margin: 0; padding: 48px; }"
                + "h1 { color: #FBBF24; background: #0F172A; margin: -48px -48px 32px; padding: 32px 48px; font-size: 26px; }"
                + "h2 { color: #0F172A; font-size: 20px; margin: 0 0 20px; }"
                + "table { width: 100%; border-collapse: collapse; margin-top: 8px; }"
                + "td { padding: 10px 0; border-bottom: 1px solid #E2E8F0; font-size: 14px; }"
                + "td.label { color: #64748B; width: 45%; }"
                + "td.value { color: #0F172A; font-weight: bold; text-align: right; }"
                + "p.footer { margin-top: 40px; color: #64748B; font-size: 11px; }"
                + "</style></head>"
                + "<body>"
                + "<h1>SWIPE AUCTIONS</h1>"
                + "<h2>Settlement Confirmation Letter</h2>"
                + "<table>"
                + row("Buyer", esc(winner.getEmail()))
                + row("Lot", esc(a.getListing().getTitle()))
                + row("Category", esc(a.getListing().getCategory().getName()))
                + row("Winning bid", money(amountPaid))
                + row("Amount settled", money(amountPaid))
                + row("Auction closed", a.getCurrentEndTime() == null ? "—" : TS.format(a.getCurrentEndTime()))
                + row("Reference", esc(a.getId().toString()))
                + "</table>"
                + "<p class=\"footer\">This letter confirms full settlement of the above auction on Swipe Auctions. "
                + "Retain it for your records. © 2026 Swipe Auctions. All Rights Reserved.</p>"
                + "</body></html>";
    }

    private static String row(String label, String value) {
        return "<tr><td class=\"label\">" + label + "</td><td class=\"value\">" + value + "</td></tr>";
    }

    private static String money(BigDecimal n) {
        return n == null ? "—" : "Rs. " + n.stripTrailingZeros().toPlainString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
