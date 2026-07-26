package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.WalletWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletWithdrawalRepository extends JpaRepository<WalletWithdrawal, UUID> {
}
