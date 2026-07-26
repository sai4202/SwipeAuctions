package com.swipeauctions.admin.service;

import com.swipeauctions.auth.helper.UserSessionManagementService;
import com.swipeauctions.common.exception.ResourceNotFoundException;
import com.swipeauctions.enums.Role;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Admin-facing user management: search/list, suspend, reactivate. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    private final UserSessionManagementService sessionManagementService;

    @Transactional(readOnly = true)
    public Page<User> listUsers(String search, Role roleFilter, Boolean activeFilter, Pageable pageable) {
        String needle = search != null ? search.trim().toLowerCase() : null;
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (needle != null && !needle.isBlank()) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), "%" + needle + "%"),
                        cb.like(cb.lower(root.get("mobileNumber")), "%" + needle + "%")));
            }
            if (roleFilter != null) {
                predicates.add(cb.equal(root.get("role"), roleFilter));
            }
            if (activeFilter != null) {
                predicates.add(cb.equal(root.get("active"), activeFilter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public User getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public User suspend(UUID id) {
        User u = getUser(id);
        u.setActive(false);
        User saved = userRepository.save(u);
        // Without this, a suspended user's already-issued JWT keeps authenticating (the filter only
        // checks that an active session row exists for the token, never user.getActive() again).
        sessionManagementService.invalidateUserSessions(u);
        return saved;
    }

    @Transactional
    public User reactivate(UUID id) {
        User u = getUser(id);
        u.setActive(true);
        u.setAccountNonLocked(true);
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        return userRepository.save(u);
    }
}
