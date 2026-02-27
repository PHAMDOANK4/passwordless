package org.openidentityplatform.passwordless.auth.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.openidentityplatform.passwordless.auth.dto.AuthResponse;
import org.openidentityplatform.passwordless.auth.models.RefreshToken;
import org.openidentityplatform.passwordless.auth.repositories.RefreshTokenRepository;
import org.openidentityplatform.passwordless.configuration.JwtProperties;
import org.openidentityplatform.passwordless.configuration.JwtProvider;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.otp.models.VerifyOtpResult;
import org.openidentityplatform.passwordless.otp.services.OtpService;
import org.openidentityplatform.passwordless.totp.services.TotpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Log4j2
public class AuthService {

    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AccountLockoutService accountLockoutService;
    private final TotpService totpService;
    private final OtpService otpService;

    @Transactional
    public AuthResponse authenticateWithTotp(String email, Integer totpCode, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or TOTP code"));

        accountLockoutService.checkLockout(user);

        try {
            boolean valid = totpService.verify(email, totpCode);
            if (!valid) {
                accountLockoutService.recordFailedAttempt(user);
                throw new IllegalArgumentException("Invalid email or TOTP code");
            }
        } catch (NotFoundException e) {
            accountLockoutService.recordFailedAttempt(user);
            throw new IllegalArgumentException("Invalid email or TOTP code");
        }

        accountLockoutService.resetFailedAttempts(user);
        return issueTokens(user, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse authenticateWithOtp(String email, String otp, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or OTP"));

        accountLockoutService.checkLockout(user);

        try {
            VerifyOtpResult result = otpService.verifyByDestination(email, otp);
            if (!result.isValid()) {
                accountLockoutService.recordFailedAttempt(user);
                throw new IllegalArgumentException("Invalid email or OTP");
            }
        } catch (NotFoundException e) {
            accountLockoutService.recordFailedAttempt(user);
            throw new IllegalArgumentException("Invalid email or OTP");
        } catch (Exception e) {
            accountLockoutService.recordFailedAttempt(user);
            throw new IllegalArgumentException("Invalid email or OTP");
        }

        accountLockoutService.resetFailedAttempts(user);
        return issueTokens(user, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenStr, String ipAddress, String userAgent) {
        if (!jwtProvider.isTokenValid(refreshTokenStr)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String userId = jwtProvider.getUserIdFromToken(refreshTokenStr);
        String tokenHash = hashToken(refreshTokenStr);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found or revoked"));

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new IllegalArgumentException("Refresh token expired");
        }

        // Revoke old token (rotation)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return issueTokens(user, ipAddress, userAgent);
    }

    @Transactional
    public void revokeRefreshToken(String refreshTokenStr) {
        String tokenHash = hashToken(refreshTokenStr);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllUserTokens(String userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private AuthResponse issueTokens(User user, String ipAddress, String userAgent) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshTokenStr = jwtProvider.generateRefreshToken(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hashToken(refreshTokenStr));
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS));
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setUserAgent(userAgent);
        refreshTokenRepository.save(refreshToken);

        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);

        long expiresIn = jwtProperties.getAccessTokenExpirationMinutes() * 60;
        return new AuthResponse(accessToken, refreshTokenStr, expiresIn, "Bearer");
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
