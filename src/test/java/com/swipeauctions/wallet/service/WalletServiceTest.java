package com.swipeauctions.wallet.service;

import com.swipeauctions.auction.entity.Auction;
import com.swipeauctions.bidding.repository.BidRepository;
import com.swipeauctions.notification.AuctionNotificationService;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.wallet.entity.BidEligibilityHold;
import com.swipeauctions.wallet.entity.Wallet;
import com.swipeauctions.wallet.entity.WalletTransaction;
import com.swipeauctions.wallet.enums.HoldStatus;
import com.swipeauctions.wallet.enums.WalletTxnType;
import com.swipeauctions.wallet.repository.BidEligibilityHoldRepository;
import com.swipeauctions.wallet.repository.SaleProceedsHoldRepository;
import com.swipeauctions.wallet.repository.WalletRepository;
import com.swipeauctions.wallet.repository.WalletTransactionRepository;
import com.swipeauctions.wallet.repository.WalletWithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wallet ledger reconciliation: drives hold→capture and hold→release sequences and asserts the
 * resulting available/held balances and recorded transaction log both match hand-computed values.
 * Pure Mockito — no DB.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository txnRepository;
    @Mock private BidEligibilityHoldRepository holdRepository;
    @Mock private WalletWithdrawalRepository withdrawalRepository;
    @Mock private SaleProceedsHoldRepository proceedsRepository;
    @Mock private AuctionNotificationService notificationService;
    @Mock private BidRepository bidRepository;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, txnRepository, holdRepository,
                withdrawalRepository, proceedsRepository, notificationService, bidRepository);
    }

    private static User user(BigDecimal unused) {
        User u = User.builder().build();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static Auction auction(BigDecimal basePrice) {
        Auction a = Auction.builder().basePrice(basePrice).build();
        a.setId(UUID.randomUUID());
        return a;
    }

    @Test
    void holdThenCapture_movesBasePriceFromAvailableToCapturedAndRecordsMatchingTransactions() {
        User bidder = user(null);
        Auction auctionEntity = auction(new BigDecimal("200.00"));
        Wallet wallet = Wallet.builder().user(bidder)
                .availableBalance(new BigDecimal("1000.00")).heldBalance(BigDecimal.ZERO).build();

        when(walletRepository.findByUser_Id(bidder.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserIdForUpdate(bidder.getId())).thenReturn(Optional.of(wallet));
        when(holdRepository.findByAuction_IdAndBidder_Id(auctionEntity.getId(), bidder.getId()))
                .thenReturn(Optional.empty());
        when(holdRepository.save(any(BidEligibilityHold.class))).thenAnswer(inv -> inv.getArgument(0));

        BidEligibilityHold hold = walletService.placeHold(bidder, auctionEntity);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("800.00");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("200.00");
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.ACTIVE);

        // Winner: capture the hold.
        when(holdRepository.findByAuction_IdAndBidder_Id(auctionEntity.getId(), bidder.getId()))
                .thenReturn(Optional.of(hold));
        lenient().when(holdRepository.save(any(BidEligibilityHold.class))).thenAnswer(inv -> inv.getArgument(0));

        walletService.captureHold(auctionEntity, bidder);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("800.00");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("0.00");
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.CAPTURED);

        ArgumentCaptor<WalletTransaction> txnCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(txnRepository, org.mockito.Mockito.times(2)).save(txnCaptor.capture());
        List<WalletTransaction> txns = txnCaptor.getAllValues();

        assertThat(txns.get(0).getType()).isEqualTo(WalletTxnType.HOLD);
        assertThat(txns.get(0).getAmount()).isEqualByComparingTo("200.00");
        assertThat(txns.get(1).getType()).isEqualTo(WalletTxnType.CAPTURE);
        assertThat(txns.get(1).getAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void holdThenRelease_returnsBasePriceToAvailableAndRecordsMatchingTransactions() {
        User bidder = user(null);
        Auction auctionEntity = auction(new BigDecimal("150.00"));
        Wallet wallet = Wallet.builder().user(bidder)
                .availableBalance(new BigDecimal("500.00")).heldBalance(BigDecimal.ZERO).build();

        when(walletRepository.findByUser_Id(bidder.getId())).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserIdForUpdate(bidder.getId())).thenReturn(Optional.of(wallet));
        when(holdRepository.findByAuction_IdAndBidder_Id(auctionEntity.getId(), bidder.getId()))
                .thenReturn(Optional.empty());
        when(holdRepository.save(any(BidEligibilityHold.class))).thenAnswer(inv -> inv.getArgument(0));

        BidEligibilityHold hold = walletService.placeHold(bidder, auctionEntity);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("350.00");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("150.00");

        // Loser: release the hold back to available.
        when(holdRepository.findByAuction_IdAndBidder_Id(auctionEntity.getId(), bidder.getId()))
                .thenReturn(Optional.of(hold));

        walletService.releaseHold(auctionEntity, bidder);

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(wallet.getHeldBalance()).isEqualByComparingTo("0.00");
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);

        ArgumentCaptor<WalletTransaction> txnCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(txnRepository, org.mockito.Mockito.times(2)).save(txnCaptor.capture());
        List<WalletTransaction> txns = txnCaptor.getAllValues();

        assertThat(txns.get(0).getType()).isEqualTo(WalletTxnType.HOLD);
        assertThat(txns.get(1).getType()).isEqualTo(WalletTxnType.RELEASE);
        assertThat(txns.get(1).getAmount()).isEqualByComparingTo("150.00");
    }
}
