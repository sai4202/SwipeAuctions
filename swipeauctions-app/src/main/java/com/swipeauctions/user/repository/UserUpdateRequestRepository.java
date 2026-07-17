package com.swipeauctions.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.entity.UserUpdateRequest;

import java.util.Optional;
import java.util.UUID;

public interface UserUpdateRequestRepository
        extends JpaRepository<UserUpdateRequest, UUID> {

    Optional<UserUpdateRequest> findByUser(
            User user
    );
}