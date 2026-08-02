package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.WalletTransaction;
import com.swipeauctions.wallet.enums.WalletTxnType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findByWallet_IdOrderByCreatedAtDesc(UUID walletId);

    List<WalletTransaction> findByType(WalletTxnType type);

    /** Admin-wide wallet ledger, optionally filtered by type. */
    Page<WalletTransaction> findByType(WalletTxnType type, Pageable pageable);
}
