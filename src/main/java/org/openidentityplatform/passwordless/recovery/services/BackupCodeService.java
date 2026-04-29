package org.openidentityplatform.passwordless.recovery.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.recovery.models.BackupCode;
import org.openidentityplatform.passwordless.recovery.repositories.BackupCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates and verifies one-time backup codes for account recovery.
 * Codes are 8-character alphanumeric strings, stored as SHA-256 hashes.
 */
@Service
@AllArgsConstructor
@Slf4j
public class BackupCodeService {

    private static final int CODE_COUNT = 10;
    private static final int CODE_LENGTH = 8;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Ambiguous chars removed

    private final BackupCodeRepository backupCodeRepository;

    /**
     * Generates a new set of backup codes for the user.
     * Old unused codes are deleted before generating new ones.
     *
     * @return List of plaintext codes (shown once to the user, never stored in plaintext)
     */
    @Transactional
    public List<String> generateCodes(User user) {
        // Delete any existing codes for this user
        backupCodeRepository.deleteByUserId(user.getId());

        SecureRandom random = new SecureRandom();
        List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);

        for (int i = 0; i < CODE_COUNT; i++) {
            String code = generateCode(random);
            plaintextCodes.add(code);

            BackupCode backupCode = new BackupCode();
            backupCode.setUser(user);
            backupCode.setCodeHash(sha256(code));
            backupCode.setUsed(false);
            backupCodeRepository.save(backupCode);
        }

        log.info("Generated {} backup codes for user {}", CODE_COUNT, user.getId());
        return plaintextCodes;
    }

    /**
     * Verifies a backup code against unused codes for the user.
     * If valid, the code is marked as used (single-use).
     *
     * @return true if the code matched and was consumed
     */
    @Transactional
    public boolean verifyCode(User user, String code) {
        String hash = sha256(code.toUpperCase().trim());
        List<BackupCode> unusedCodes = backupCodeRepository.findByUserIdAndUsedFalse(user.getId());

        for (BackupCode bc : unusedCodes) {
            if (bc.getCodeHash().equals(hash) && bc.isValid()) {
                bc.markAsUsed();
                backupCodeRepository.save(bc);
                log.info("Backup code consumed for user {}", user.getId());
                return true;
            }
        }

        log.warn("Invalid backup code attempt for user {}", user.getId());
        return false;
    }

    /**
     * Returns the count of remaining unused backup codes.
     */
    public long getRemainingCount(String userId) {
        return backupCodeRepository.countByUserIdAndUsedFalse(userId);
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    private String generateCode(SecureRandom random) {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
