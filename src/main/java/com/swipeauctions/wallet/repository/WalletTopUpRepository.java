package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.WalletTopUp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletTopUpRepository extends JpaRepository<WalletTopUp, UUID> {

    Optional<WalletTopUp> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Pessimistic row lock for webhook processing — Stripe can and does redeliver the same event
     * (e.g. on a slow response), so two near-simultaneous deliveries for the same PaymentIntent must
     * serialize on this row rather than both reading PENDING before either commits its status update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WalletTopUp t where t.stripePaymentIntentId = :piId")
    Optional<WalletTopUp> findByStripePaymentIntentIdForUpdate(@Param("piId") String stripePaymentIntentId);
}
