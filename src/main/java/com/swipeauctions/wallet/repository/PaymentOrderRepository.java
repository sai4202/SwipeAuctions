package com.swipeauctions.wallet.repository;

import com.swipeauctions.wallet.entity.PaymentOrder;
import com.swipeauctions.wallet.enums.TopUpStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    /** Admin-wide wallet top-up queue, optionally filtered by status. */
    Page<PaymentOrder> findByStatus(TopUpStatus status, Pageable pageable);

    /**
     * Pessimistic row lock for settlement — both the client-side verify call and Razorpay's webhook
     * (which can and does redeliver) can arrive for the same order, so they must serialize on this
     * row rather than both reading PENDING before either commits its status update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.razorpayOrderId = :orderId")
    Optional<PaymentOrder> findByRazorpayOrderIdForUpdate(@Param("orderId") String razorpayOrderId);
}
