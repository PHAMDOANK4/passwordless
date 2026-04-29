package org.openidentityplatform.passwordless.recovery.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.recovery.models.MfaResetRequest;
import org.openidentityplatform.passwordless.recovery.models.RecoveryRequest;
import org.openidentityplatform.passwordless.recovery.models.RecoveryRequestResponse;
import org.openidentityplatform.passwordless.recovery.models.RecoveryVerifyRequest;
import org.openidentityplatform.passwordless.recovery.models.RecoveryVerifyResponse;
import org.openidentityplatform.passwordless.recovery.services.BackupCodeService;
import org.openidentityplatform.passwordless.recovery.services.RecoveryService;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Account recovery endpoints for backup codes and MFA reset.
 */
@RestController
@RequestMapping("/recovery")
@AllArgsConstructor
public class RecoveryController {

    private final RecoveryService recoveryService;
    private final BackupCodeService backupCodeService;
    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    /**
     * Request account recovery — sends a recovery OTP to the user's email.
     * Rate limited to 3 requests per email per hour.
     */
    @PostMapping("/request")
    public RecoveryRequestResponse requestRecovery(
            @RequestBody @Valid RecoveryRequest request,
            HttpServletRequest httpRequest) {
        return recoveryService.requestRecovery(request.getEmail(), httpRequest.getRemoteAddr());
    }

    /**
     * Verify a recovery code (backup code or email OTP).
     * On success, returns a single-use recovery token for MFA reset.
     */
    @PostMapping("/verify")
    public RecoveryVerifyResponse verifyRecovery(
            @RequestBody @Valid RecoveryVerifyRequest request) {
        return recoveryService.verifyRecovery(request.getEmail(), request.getCode(), request.getType());
    }

    /**
     * Reset MFA settings using a verified recovery token.
     * Removes all TOTP and WebAuthn registrations.
     */
    @PostMapping("/reset-mfa")
    public ResponseEntity<Map<String, String>> resetMfa(
            @RequestBody @Valid MfaResetRequest request) {
        try {
            recoveryService.resetMfa(request.getRecoveryToken());
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "MFA has been reset. Please set up new authentication methods."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Generate new backup codes for the authenticated user.
     * Requires a valid Bearer access token.
     * Old backup codes are deleted and replaced.
     */
    @PostMapping("/backup-codes")
    public ResponseEntity<Map<String, Object>> generateBackupCodes(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = resolveUser(authorization);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        List<String> codes = backupCodeService.generateCodes(user);
        return ResponseEntity.ok(Map.of(
                "codes", codes,
                "count", codes.size(),
                "message", "Save these codes in a secure location. They will not be shown again."
        ));
    }

    /**
     * Get the remaining unused backup code count for the authenticated user.
     */
    @GetMapping("/backup-codes/count")
    public ResponseEntity<Map<String, Object>> getBackupCodeCount(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = resolveUser(authorization);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        long remaining = backupCodeService.getRemainingCount(user.getId());
        return ResponseEntity.ok(Map.of("remaining", remaining));
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    private User resolveUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authorization.substring("Bearer ".length()).trim();
            var claims = jwtTokenService.validateAccessToken(token);
            return userRepository.findById(claims.getSubject()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
