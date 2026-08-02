package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.WalletWithdrawal;
import com.swipeauctions.wallet.enums.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletWithdrawalRepository extends JpaRepository<WalletWithdrawal, UUID> {

    /** Admin-wide payout monitoring queue, optionally filtered by status. */
    Page<WalletWithdrawal> findByStatus(WithdrawalStatus status, Pageable pageable);
}
