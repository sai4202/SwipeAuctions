package com.swipeauctions.auth.scheduler;

import com.swipeauctions.auth.config.StaleRegistrationCleanupConfig;
import com.swipeauctions.auth.helper.UserRegistrationHelperService;
import com.swipeauctions.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The purge tick: respects the enabled kill-switch and isolates one bad row from the rest. */
@ExtendWith(MockitoExtension.class)
class StaleRegistrationCleanupSchedulerTest {

    @Mock private UserRegistrationHelperService registrationHelperService;
    @Mock private StaleRegistrationCleanupConfig config;

    private StaleRegistrationCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StaleRegistrationCleanupScheduler(registrationHelperService, config);
    }

    @Test
    void disabled_skipsEntirely() {
        when(config.isEnabled()).thenReturn(false);

        scheduler.purgeStaleRegistrations();

        verify(registrationHelperService, never()).findStaleUnverifiedUsers(anyLong());
    }

    @Test
    void oneFailingRow_doesNotStopTheRestFromBeingPurged() {
        User first = User.builder().email("first@example.com").build();
        first.setId(UUID.randomUUID());
        User second = User.builder().email("second@example.com").build();
        second.setId(UUID.randomUUID());

        when(config.isEnabled()).thenReturn(true);
        when(config.getThresholdHours()).thenReturn(24L);
        when(registrationHelperService.findStaleUnverifiedUsers(24L)).thenReturn(List.of(first, second));
        when(registrationHelperService.purgeIfStillUnverified(eq(first.getId()), eq("first@example.com")))
                .thenThrow(new RuntimeException("simulated FK violation"));
        when(registrationHelperService.purgeIfStillUnverified(eq(second.getId()), eq("second@example.com")))
                .thenReturn(true);

        scheduler.purgeStaleRegistrations();

        verify(registrationHelperService).purgeIfStillUnverified(first.getId(), "first@example.com");
        verify(registrationHelperService).purgeIfStillUnverified(second.getId(), "second@example.com");
    }
}
