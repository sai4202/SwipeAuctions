package com.swipeauctions.admin.controller;

import com.swipeauctions.auction.enums.AuctionStatus;
import com.swipeauctions.auction.repository.AuctionRepository;
import com.swipeauctions.dispute.enums.DisputeStatus;
import com.swipeauctions.dispute.repository.DisputeRepository;
import com.swipeauctions.enums.KycStatus;
import com.swipeauctions.enums.BillingCycle;
import com.swipeauctions.enums.SubscriptionTier;
import com.swipeauctions.payment.RazorpayConfig;
import com.swipeauctions.user.entity.KycVerification;
import com.swipeauctions.user.repository.KycVerificationRepository;
import com.swipeauctions.user.repository.UserRepository;
import com.swipeauctions.wallet.entity.WalletTransaction;
import com.swipeauctions.wallet.enums.TopUpStatus;
import com.swipeauctions.wallet.enums.WalletTxnType;
import com.swipeauctions.wallet.repository.PaymentOrderRepository;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything the Overview dashboard needs beyond the existing stats/analytics/holds endpoints —
 * kept as its own controller (same reasoning as AdminAuditLogController/AdminChatController) since
 * AdminController is already large. Every number here is a real read over existing data; nothing
 * fabricated to fill a panel.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final DisputeRepository disputeRepository;
    private final KycVerificationRepository kycVerificationRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final RazorpayConfig razorpayConfig;

    @GetMapping("/summary")
    public SummaryResponse summary() {
        long totalUsers = userRepository.count();
        long openAuctions = auctionRepository.findByStatus(AuctionStatus.OPEN).size();
        long openDisputes = disputeRepository.findByStatus(DisputeStatus.OPEN).size()
                + disputeRepository.findByStatus(DisputeStatus.IN_REVIEW).size();
        BigDecimal gmv = walletTransactionRepository.findByType(WalletTxnType.CAPTURE).stream()
                .map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        List<WalletTransaction> today = walletTransactionRepository.findAll().stream()
                .filter(t -> !t.getCreatedAt().isBefore(startOfToday)).toList();
        long transactionsToday = today.size();
        BigDecimal transactionsAmountToday = today.stream().map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        long kycVerified = kycVerificationRepository.findByStatus(KycStatus.APPROVED).size();
        long kycPending = kycVerificationRepository.findByStatus(KycStatus.PENDING).size();
        long kycRejected = kycVerificationRepository.findByStatus(KycStatus.REJECTED).size();
        long pendingPayments = paymentOrderRepository.findAll().stream()
                .filter(o -> o.getStatus() == TopUpStatus.PENDING).count();
        long pendingApprovals = kycPending + pendingPayments;

        int totalPlans = (SubscriptionTier.values().length - 1) * BillingCycle.values().length; // exclude NONE

        return new SummaryResponse(totalUsers, openAuctions, gmv, openDisputes,
                transactionsToday, transactionsAmountToday, pendingApprovals, kycRejected,
                kycVerified, kycPending, pendingPayments, totalPlans,
                razorpayConfig.isEnabled(), razorpayConfig.payoutsEnabled());
    }

    public enum Range { TODAY, YESTERDAY, LAST_7_DAYS, LAST_30_DAYS }

    /** Deposits (wallet top-ups) vs Withdrawals (seller payouts), bucketed by day — the reference
     *  dashboard's "Platform Cash Flow" chart, minus the "Investments" series (no such product here). */
    @GetMapping("/cash-flow")
    public List<CashFlowPoint> cashFlow(@RequestParam(defaultValue = "LAST_7_DAYS") Range range) {
        int days = switch (range) {
            case TODAY, YESTERDAY -> 1;
            case LAST_7_DAYS -> 7;
            case LAST_30_DAYS -> 30;
        };
        LocalDate end = range == Range.YESTERDAY ? LocalDate.now().minusDays(1) : LocalDate.now();
        LocalDate start = end.minusDays(days - 1);

        Map<LocalDate, BigDecimal> deposits = walletTransactionRepository.findByType(WalletTxnType.TOPUP).stream()
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().toLocalDate(), Collectors.reducing(BigDecimal.ZERO, WalletTransaction::getAmount, BigDecimal::add)));
        Map<LocalDate, BigDecimal> withdrawals = walletTransactionRepository.findByType(WalletTxnType.WITHDRAWAL).stream()
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().toLocalDate(), Collectors.reducing(BigDecimal.ZERO, WalletTransaction::getAmount, BigDecimal::add)));

        return start.datesUntil(end.plusDays(1))
                .map(d -> new CashFlowPoint(
                        d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        deposits.getOrDefault(d, BigDecimal.ZERO), withdrawals.getOrDefault(d, BigDecimal.ZERO)))
                .toList();
    }

    /** Verified-KYC users grouped by state / city — the reference dashboard's "Users by
     *  State"/"Users by District" panels. Only rows with a non-blank state/city count. */
    @GetMapping("/geo")
    public GeoResponse geo() {
        List<KycVerification> withLocation = kycVerificationRepository.findAll().stream()
                .filter(k -> k.getState() != null && !k.getState().isBlank()).toList();

        List<GeoRow> byState = withLocation.stream()
                .collect(Collectors.groupingBy(k -> k.getState().trim(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new GeoRow(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(GeoRow::count).reversed())
                .toList();

        List<GeoRow> byDistrict = withLocation.stream()
                .filter(k -> k.getCity() != null && !k.getCity().isBlank())
                .collect(Collectors.groupingBy(k -> k.getCity().trim(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new GeoRow(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(GeoRow::count).reversed())
                .limit(8)
                .toList();

        return new GeoResponse(byState, byDistrict);
    }

    public record SummaryResponse(long totalUsers, long openAuctions, BigDecimal gmv, long openDisputes,
                                  long transactionsToday, BigDecimal transactionsAmountToday,
                                  long pendingApprovals, long kycRejected,
                                  long kycVerified, long kycPending, long pendingPayments, int totalPlans,
                                  boolean paymentsGatewayConfigured, boolean payoutsGatewayConfigured) {}

    public record CashFlowPoint(String label, BigDecimal deposits, BigDecimal withdrawals) {}

    public record GeoRow(String name, long count) {}

    public record GeoResponse(List<GeoRow> byState, List<GeoRow> byDistrict) {}
}
