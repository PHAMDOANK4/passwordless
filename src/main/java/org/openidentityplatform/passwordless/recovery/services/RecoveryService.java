package org.openidentityplatform.passwordless.recovery.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.otp.services.OtpService;
import org.openidentityplatform.passwordless.recovery.models.RecoveryRequestResponse;
import org.openidentityplatform.passwordless.recovery.models.RecoveryVerifyResponse;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorJPARepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Coordinates the account recovery flow:
 * <ol>
 *   <li>Request: Send a recovery OTP to the user's email</li>
 *   <li>Verify: Validate the OTP or a backup code → issue a recovery token</li>
 *   <li>Reset MFA: Use the recovery token to reset MFA settings</li>
 * </ol>
 * <p>
 * Recovery tokens are stored in Redis with a 15-minute TTL and are single-use.
 */
@Service
@AllArgsConstructor
@Slf4j
public class RecoveryService {

    private static final String RECOVERY_TOKEN_PREFIX = "recovery:token:";
    private static final Duration RECOVERY_TOKEN_TTL = Duration.ofMinutes(15);
    private static final String RATE_LIMIT_PREFIX = "recovery:rate:";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_HOUR = 3;

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final BackupCodeService backupCodeService;
    private final RegisteredTotpRepository registeredTotpRepository;
    private final UserAuthenticatorJPARepository webAuthnRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Initiates a recovery request by sending an OTP to the user's email.
     * Rate limited to {@value MAX_REQUESTS_PER_HOUR} requests per email per hour.
     */
    public RecoveryRequestResponse requestRecovery(String email, String ipAddress) {
        // Rate limiting
        String rateLimitKey = RATE_LIMIT_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateLimitKey, RATE_LIMIT_WINDOW);
        }
        if (count != null && count > MAX_REQUESTS_PER_HOUR) {
            log.warn("Recovery rate limit exceeded for email={} from ip={}", email, ipAddress);
            return new RecoveryRequestResponse(
                    "Too many recovery requests. Please try again later.", null);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Always return success to avoid user enumeration
            log.info("Recovery requested for unknown email={}", email);
            return new RecoveryRequestResponse(
                    "If the email is registered, a recovery code has been sent.", 900L);
        }

        // Send OTP via email (reuse existing OTP sender)
        try {
            otpService.send("emailLink", email);
        } catch (Exception e) {
            log.error("Failed to send recovery OTP to {}", email, e);
            // Still return success to avoid enumeration
        }

        log.info("Recovery OTP sent to email={} from ip={}", email, ipAddress);
        return new RecoveryRequestResponse(
                "If the email is registered, a recovery code has been sent.", 900L);
    }

    /**
     * Verifies a recovery code (either backup code or email OTP).
     * On success, issues a single-use recovery token for MFA reset.
     */
    public RecoveryVerifyResponse verifyRecovery(String email, String code, String type) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return new RecoveryVerifyResponse(false, null, "Invalid recovery attempt");
        }
        User user = userOpt.get();

        boolean verified = false;

        if ("backup".equalsIgnoreCase(type)) {
            verified = backupCodeService.verifyCode(user, code);
        } else if ("otp".equalsIgnoreCase(type)) {
            try {
                var result = otpService.verifyByDestination(email, code);
                verified = result.isValid();
            } catch (Exception e) {
                log.warn("OTP verification failed for recovery: {}", e.getMessage());
            }
        }

        if (!verified) {
            return new RecoveryVerifyResponse(false, null, "Invalid or expired code");
        }

        // Issue recovery token (stored in Redis, 15-min TTL, single-use)
        String recoveryToken = generateRecoveryToken();
        redisTemplate.opsForValue().set(
                RECOVERY_TOKEN_PREFIX + recoveryToken,
                user.getId(),
                RECOVERY_TOKEN_TTL
        );

        log.info("Recovery verified for user={} via type={}", user.getId(), type);
        return new RecoveryVerifyResponse(true, recoveryToken, "Recovery verified");
    }

    /**
     * Resets MFA settings using a verified recovery token.
     * Deletes TOTP registrations and WebAuthn credentials, disables MFA.
     */
    @Transactional
    public void resetMfa(String recoveryToken) {
        String userId = redisTemplate.opsForValue().get(RECOVERY_TOKEN_PREFIX + recoveryToken);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid or expired recovery token");
        }

        // Consume the token (single-use)
        redisTemplate.delete(RECOVERY_TOKEN_PREFIX + recoveryToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Delete TOTP registrations
        registeredTotpRepository.deleteByUserId(userId);

        // Delete WebAuthn credentials
        webAuthnRepository.deleteByUserId(userId);

        // Disable MFA on the user record
        user.setMfaEnabled(false);
        user.setPreferredMfaMethod(null);
        userRepository.save(user);

        log.info("MFA reset completed for user={}", userId);
    }

    private String generateRecoveryToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
