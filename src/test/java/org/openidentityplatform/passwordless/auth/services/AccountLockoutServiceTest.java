package org.openidentityplatform.passwordless.auth.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountLockoutServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountLockoutService accountLockoutService;

    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        user = new User();
        user.setId("user-123");
        user.setEmail("user@test.com");
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }

    @Test
    void testCheckLockout_notLocked() {
        // Given - user is not locked
        // When & Then - no exception
        assertDoesNotThrow(() -> accountLockoutService.checkLockout(user));
    }

    @Test
    void testCheckLockout_locked() {
        // Given
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));

        // When & Then
        assertThrows(AccountLockedException.class, () -> accountLockoutService.checkLockout(user));
    }

    @Test
    void testRecordFailedAttempt_incrementCount() {
        // Given
        user.setFailedLoginAttempts(1);
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        accountLockoutService.recordFailedAttempt(user);

        // Then
        assertEquals(2, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepository).save(user);
    }

    @Test
    void testRecordFailedAttempt_lockAccount() {
        // Given - 4 failed attempts already
        user.setFailedLoginAttempts(4);
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        accountLockoutService.recordFailedAttempt(user);

        // Then
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(Instant.now()));
        verify(userRepository).save(user);
    }

    @Test
    void testResetFailedAttempts() {
        // Given
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        accountLockoutService.resetFailedAttempts(user);

        // Then
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepository).save(user);
    }
}
