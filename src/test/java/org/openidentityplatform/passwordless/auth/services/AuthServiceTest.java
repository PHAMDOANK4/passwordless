package org.openidentityplatform.passwordless.auth.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openidentityplatform.passwordless.auth.dto.AuthResponse;
import org.openidentityplatform.passwordless.auth.models.RefreshToken;
import org.openidentityplatform.passwordless.auth.repositories.RefreshTokenRepository;
import org.openidentityplatform.passwordless.configuration.JwtProperties;
import org.openidentityplatform.passwordless.configuration.JwtProvider;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.otp.services.OtpService;
import org.openidentityplatform.passwordless.totp.services.TotpService;
import org.openidentityplatform.passwordless.totp.services.UserNotFoundException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountLockoutService accountLockoutService;

    @Mock
    private TotpService totpService;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private Domain domain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        domain = new Domain();
        domain.setId("domain-1");
        domain.setDomainName("test.com");

        user = new User();
        user.setId("user-123");
        user.setEmail("user@test.com");
        user.setDomain(domain);
    }

    @Test
    void testAuthenticateWithTotp_success() throws UserNotFoundException {
        // Given
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doNothing().when(accountLockoutService).checkLockout(user);
        when(totpService.verify("user@test.com", 123456)).thenReturn(true);
        doNothing().when(accountLockoutService).resetFailedAttempts(user);

        when(jwtProvider.generateAccessToken("user-123", "user@test.com")).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken("user-123")).thenReturn("refresh-token");
        when(jwtProperties.getRefreshTokenExpirationDays()).thenReturn(7L);
        when(jwtProperties.getAccessTokenExpirationMinutes()).thenReturn(15L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        AuthResponse response = authService.authenticateWithTotp("user@test.com", 123456, "127.0.0.1", "TestAgent");

        // Then
        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(900, response.getExpiresIn());
        assertEquals("Bearer", response.getTokenType());
        verify(accountLockoutService).resetFailedAttempts(user);
    }

    @Test
    void testAuthenticateWithTotp_invalidCode() throws UserNotFoundException {
        // Given
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doNothing().when(accountLockoutService).checkLockout(user);
        when(totpService.verify("user@test.com", 999999)).thenReturn(false);

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                authService.authenticateWithTotp("user@test.com", 999999, "127.0.0.1", "TestAgent"));

        verify(accountLockoutService).recordFailedAttempt(user);
    }

    @Test
    void testAuthenticateWithTotp_lockedAccount() {
        // Given
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doThrow(new AccountLockedException("Account is locked"))
                .when(accountLockoutService).checkLockout(user);

        // When & Then
        assertThrows(AccountLockedException.class, () ->
                authService.authenticateWithTotp("user@test.com", 123456, "127.0.0.1", "TestAgent"));
    }

    @Test
    void testRefreshToken_success() {
        // Given
        String oldRefreshToken = "old-refresh-token";
        when(jwtProvider.isTokenValid(oldRefreshToken)).thenReturn(true);
        when(jwtProvider.getUserIdFromToken(oldRefreshToken)).thenReturn("user-123");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setId("token-id");
        storedToken.setUserId("user-123");
        storedToken.setTokenHash("some-hash");
        storedToken.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        storedToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        when(jwtProvider.generateAccessToken("user-123", "user@test.com")).thenReturn("new-access-token");
        when(jwtProvider.generateRefreshToken("user-123")).thenReturn("new-refresh-token");
        when(jwtProperties.getRefreshTokenExpirationDays()).thenReturn(7L);
        when(jwtProperties.getAccessTokenExpirationMinutes()).thenReturn(15L);
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        AuthResponse response = authService.refreshToken(oldRefreshToken, "127.0.0.1", "TestAgent");

        // Then
        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertTrue(storedToken.isRevoked());
    }

    @Test
    void testRevokeRefreshToken() {
        // Given
        String refreshTokenStr = "refresh-token-to-revoke";
        RefreshToken storedToken = new RefreshToken();
        storedToken.setId("token-id");
        storedToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // When
        authService.revokeRefreshToken(refreshTokenStr);

        // Then
        assertTrue(storedToken.isRevoked());
        verify(refreshTokenRepository).save(storedToken);
    }
}
