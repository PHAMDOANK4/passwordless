package org.openidentityplatform.passwordless.admin;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.otp.models.SentOtp;
import org.openidentityplatform.passwordless.otp.repositories.SentOtpRepository;
import org.openidentityplatform.passwordless.totp.models.RegisteredTotp;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorJPARepository;
import org.openidentityplatform.passwordless.webauthn.repositories.WebAuthnAuthenticatorEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin User Management API - Read and manage users + their auth keys.
 * Does NOT modify any authentication business logic.
 */
@RestController
@RequestMapping("/admin/api/users")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RegisteredTotpRepository totpRepository;
    private final UserAuthenticatorJPARepository webAuthnRepository;
    private final SentOtpRepository sentOtpRepository;

    // =========================================================
    // USER CRUD
    // =========================================================

    /**
     * List users with pagination, search, and status filter.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> usersPage;

        if (StringUtils.hasText(search)) {
            usersPage = userRepository.searchByEmailOrDisplayName(search, pageable);
        } else if (StringUtils.hasText(status)) {
            try {
                User.UserStatus userStatus = User.UserStatus.valueOf(status.toUpperCase());
                usersPage = userRepository.findByStatus(userStatus, pageable);
            } catch (IllegalArgumentException e) {
                usersPage = userRepository.findAll(pageable);
            }
        } else {
            usersPage = userRepository.findAll(pageable);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("users", usersPage.getContent().stream().map(this::toUserSummary).collect(Collectors.toList()));
        response.put("totalElements", usersPage.getTotalElements());
        response.put("totalPages", usersPage.getTotalPages());
        response.put("currentPage", page);
        response.put("pageSize", size);

        return ResponseEntity.ok(response);
    }

    /**
     * Get full user details including auth keys summary.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserDetail(@PathVariable String id) {
        return userRepository.findById(id)
                .map(user -> {
                    Map<String, Object> detail = toUserDetail(user);

                    // Count registered keys
                    long totpCount = totpRepository.countByUserId(id);
                    long passkeyCount = webAuthnRepository.countByUserId(id);

                    detail.put("totpKeyCount", totpCount);
                    detail.put("passkeyCount", passkeyCount);

                    return ResponseEntity.ok(detail);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update basic user info (status, role, mfaEnabled). Does NOT touch auth logic.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates
    ) {
        return userRepository.findById(id)
                .map(user -> {
                    if (updates.containsKey("status")) {
                        try {
                            user.setStatus(User.UserStatus.valueOf(updates.get("status").toString().toUpperCase()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    if (updates.containsKey("role")) {
                        try {
                            user.setRole(User.UserRole.valueOf(updates.get("role").toString().toUpperCase()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    if (updates.containsKey("firstName")) {
                        user.setFirstName(updates.get("firstName").toString());
                    }
                    if (updates.containsKey("lastName")) {
                        user.setLastName(updates.get("lastName").toString());
                    }
                    if (updates.containsKey("displayName")) {
                        user.setDisplayName(updates.get("displayName").toString());
                    }
                    if (updates.containsKey("mfaEnabled")) {
                        user.setMfaEnabled(Boolean.parseBoolean(updates.get("mfaEnabled").toString()));
                    }
                    User saved = userRepository.save(user);
                    return ResponseEntity.ok(toUserDetail(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Suspend a user account.
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspendUser(@PathVariable String id) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setStatus(User.UserStatus.SUSPENDED);
                    userRepository.save(user);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Activate a suspended user account.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable String id) {
        return userRepository.findById(id)
                .map(user -> {
                    user.setStatus(User.UserStatus.ACTIVE);
                    user.setFailedLoginAttempts(0);
                    user.setLockedUntil(null);
                    userRepository.save(user);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a user. This only removes the IAM user record.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Delete dependent auth records first to avoid FK constraint violations.
            totpRepository.deleteByUserId(id);
            webAuthnRepository.deleteByUserId(id);
            userRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(409).build();
        }
    }

    // =========================================================
    // TOTP KEYS
    // =========================================================

    /**
     * Get TOTP keys registered for a user.
     */
    @GetMapping("/{id}/totp-keys")
    public ResponseEntity<List<Map<String, Object>>> getTotpKeys(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<RegisteredTotp> totps = totpRepository.findByUserIdOrderByUsername(id);
        List<Map<String, Object>> result = totps.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("username", t.getUsername());
            m.put("hasSecret", t.getSecret() != null && !t.getSecret().isEmpty());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a TOTP key for a user.
     */
    @DeleteMapping("/{id}/totp-keys/{keyId}")
    public ResponseEntity<Void> deleteTotpKey(@PathVariable String id, @PathVariable String keyId) {
        return totpRepository.findById(keyId)
                .filter(t -> id.equals(t.getUser() != null ? t.getUser().getId() : null))
                .map(t -> {
                    totpRepository.delete(t);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // PASSKEYS (WebAuthn)
    // =========================================================

    /**
     * Get Passkeys (WebAuthn authenticators) registered for a user.
     */
    @GetMapping("/{id}/passkeys")
    public ResponseEntity<List<Map<String, Object>>> getPasskeys(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<WebAuthnAuthenticatorEntity> passkeys = webAuthnRepository.findByUserIdOrderByCreatedAtDesc(id);
        List<Map<String, Object>> result = passkeys.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("username", p.getUsername());
            m.put("credentialId", p.getCredentialId());
            m.put("deviceName", p.getDeviceName());
            m.put("attestationType", p.getAttestationType());
            m.put("transports", p.getTransports());
            m.put("counter", p.getCounter());
            m.put("backupEligible", p.getBackupEligible());
            m.put("backedUp", p.getBackedUp());
            m.put("createdAt", p.getCreatedAt());
            m.put("lastUsedAt", p.getLastUsedAt());
            m.put("userAgent", p.getUserAgent());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Delete a Passkey for a user.
     */
    @DeleteMapping("/{id}/passkeys/{keyId}")
    public ResponseEntity<Void> deletePasskey(@PathVariable String id, @PathVariable Long keyId) {
        return webAuthnRepository.findById(keyId)
                .filter(p -> id.equals(p.getUser() != null ? p.getUser().getId() : null))
                .map(p -> {
                    webAuthnRepository.delete(p);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // OTP SESSIONS
    // =========================================================

    /**
     * Get recent OTP sessions by destination (email/phone) for a user.
     */
    @GetMapping("/{id}/otp-sessions")
    public ResponseEntity<List<Map<String, Object>>> getOtpSessions(@PathVariable String id) {
        return userRepository.findById(id)
                .map(user -> {
                    // We query by email or phone number as destination
                    String destination = user.getPhoneNumber() != null ? user.getPhoneNumber() : user.getEmail();
                    List<SentOtp> otps = destination != null
                            ? sentOtpRepository.findByDestinationOrderByLastSentAtDesc(destination)
                            : Collections.emptyList();

                    List<Map<String, Object>> result = otps.stream().map(o -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("sessionId", o.getSessionId());
                        m.put("destination", o.getDestination());
                        m.put("attempts", o.getAttempts());
                        m.put("expireTime", o.getExpireTime());
                        m.put("lastSentAt", o.getLastSentAt());
                        boolean expired = o.getExpireTime() < Instant.now().toEpochMilli();
                        m.put("expired", expired);
                        return m;
                    }).collect(Collectors.toList());

                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private Map<String, Object> toUserSummary(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("displayName", u.getDisplayName());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("status", u.getStatus());
        m.put("role", u.getRole());
        m.put("mfaEnabled", u.isMfaEnabled());
        m.put("preferredMfaMethod", u.getPreferredMfaMethod());
        m.put("createdAt", u.getCreatedAt());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("failedLoginAttempts", u.getFailedLoginAttempts());
        m.put("locked", u.isLocked());
        
        List<String> authMethods = new ArrayList<>();
        if (totpRepository.countByUserId(u.getId()) > 0) authMethods.add("TOTP");
        if (webAuthnRepository.countByUserId(u.getId()) > 0) authMethods.add("PASSKEY");
        if (u.getPhoneNumber() != null || u.getEmail() != null) authMethods.add("OTP");
        m.put("authMethods", authMethods);
        
        return m;
    }

    private Map<String, Object> toUserDetail(User u) {
        Map<String, Object> m = toUserSummary(u);
        m.put("phoneNumber", u.getPhoneNumber());
        m.put("locale", u.getLocale());
        m.put("timezone", u.getTimezone());
        m.put("lastLoginIp", u.getLastLoginIp());
        m.put("updatedAt", u.getUpdatedAt());
        m.put("lockedUntil", u.getLockedUntil());
        m.put("externalId", u.getExternalId());
        m.put("profilePictureUrl", u.getProfilePictureUrl());
        return m;
    }
}
