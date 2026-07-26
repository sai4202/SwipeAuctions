package com.swipeauctions.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.session.entity.UserSessions;
import com.swipeauctions.user.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSessions, UUID> {

    /**
     * Returns count of active sessions for a user.
     */
    long countByUserAndActiveTrue(User user);

    /**
     * Returns active session by JWT ID.
     */
    Optional<UserSessions> findByJwtIdAndActiveTrue(String jwtId);

    /**
     * Returns session by JWT ID.
     */
    Optional<UserSessions> findByJwtId(String jwtId);

    /**
     * Returns all active sessions of a user.
     */
    List<UserSessions> findByUserAndActiveTrue(User user);

    /**
     * Returns all active sessions ordered by latest login.
     */
    List<UserSessions> findByUserAndActiveTrueOrderByLoginTimeDesc(User user);

    /**
     * Whether this user has ever had a session from the given device (User-Agent), active or not.
     * Used to send a "new device sign-in" alert only the first time a device is seen.
     */
    boolean existsByUserAndUserAgent(User user, String userAgent);
}