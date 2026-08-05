package com.swipeauctions.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.swipeauctions.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository
        extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(String mobileNumber);

    boolean existsByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);

    Optional<User> findByRazorpayContactId(String razorpayContactId);

    List<User> findByActiveFalseAndCreatedAtBefore(LocalDateTime cutoff);

    /** Re-checks active=false at delete time so a user who verifies in the window between the
     *  cleanup scheduler's select and this delete is never removed. Returns rows deleted (0 or 1). */
    @Modifying
    @Query("delete from User u where u.id = :id and u.active = false")
    int deleteIfStillUnverified(@Param("id") UUID id);
}
