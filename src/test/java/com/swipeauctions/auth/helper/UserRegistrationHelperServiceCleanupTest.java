package com.swipeauctions.auth.helper;

import com.swipeauctions.admin.repository.AdminRepository;
import com.swipeauctions.auth.util.UserReferenceNumGenerator;
import com.swipeauctions.user.entity.User;
import com.swipeauctions.user.repository.OtpVerificationRepository;
import com.swipeauctions.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Stale-unverified-registration cleanup: finding candidates and purging them safely. */
@ExtendWith(MockitoExtension.class)
class UserRegistrationHelperServiceCleanupTest {

    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private OtpVerificationRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserAuthHelperService authHelperService;
    @Mock private UserReferenceNumGenerator userReferenceNumberGenerator;

    private UserRegistrationHelperService helperService;

    @BeforeEach
    void setUp() {
        helperService = new UserRegistrationHelperService(userRepository, adminRepository,
                otpRepository, passwordEncoder, authHelperService, userReferenceNumberGenerator);
    }

    @Test
    void findStaleUnverifiedUsers_delegatesWithCutoffDerivedFromThreshold() {
        User stale = User.builder().email("stale@example.com").build();
        when(userRepository.findByActiveFalseAndCreatedAtBefore(any())).thenReturn(List.of(stale));

        List<User> result = helperService.findStaleUnverifiedUsers(24);

        assertThat(result).containsExactly(stale);
        verify(userRepository).findByActiveFalseAndCreatedAtBefore(
                org.mockito.ArgumentMatchers.argThat(cutoff ->
                        cutoff.isBefore(LocalDateTime.now().minusHours(23))
                                && cutoff.isAfter(LocalDateTime.now().minusHours(25))));
    }

    @Test
    void purgeIfStillUnverified_deletesOtpWhenUserDeleteSucceeds() {
        UUID id = UUID.randomUUID();
        when(userRepository.deleteIfStillUnverified(id)).thenReturn(1);

        boolean result = helperService.purgeIfStillUnverified(id, "stale@example.com");

        assertThat(result).isTrue();
        verify(otpRepository).deleteByEmail(eq("stale@example.com"));
    }

    @Test
    void purgeIfStillUnverified_skipsOtpWhenUserWasAlreadyActivatedInTheRaceWindow() {
        UUID id = UUID.randomUUID();
        when(userRepository.deleteIfStillUnverified(id)).thenReturn(0);

        boolean result = helperService.purgeIfStillUnverified(id, "verified@example.com");

        assertThat(result).isFalse();
        verify(otpRepository, never()).deleteByEmail(any());
    }
}
