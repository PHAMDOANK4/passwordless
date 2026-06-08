package org.openidentityplatform.passwordless.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.apps.repositories.AuditLogRepository;
import org.openidentityplatform.passwordless.iam.dto.CreateUserRequest;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.magiclink.repositories.MagicLinkRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.UserConsentRepository;
import org.openidentityplatform.passwordless.otp.models.SentOtp;
import org.openidentityplatform.passwordless.otp.repositories.SentOtpRepository;
import org.openidentityplatform.passwordless.recovery.repositories.BackupCodeRepository;
import org.openidentityplatform.passwordless.oauth2.models.Session;
import org.openidentityplatform.passwordless.oauth2.repositories.AuthorizationCodeRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.SessionRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.TokenRepository;
import org.openidentityplatform.passwordless.oauth2.services.SessionService;
import org.openidentityplatform.passwordless.totp.models.RegisteredTotp;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorJPARepository;
import org.openidentityplatform.passwordless.webauthn.repositories.WebAuthnAuthenticatorEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Locale;
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
    private final DomainRepository domainRepository;
    private final RegisteredTotpRepository totpRepository;
    private final UserAuthenticatorJPARepository webAuthnRepository;
    private final SentOtpRepository sentOtpRepository;
    private final MagicLinkRepository magicLinkRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserConsentRepository userConsentRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final SessionService sessionService;
    private final AuthorizationCodeRepository authorizationCodeRepository;
    private final TokenRepository tokenRepository;
    private final SessionRepository userSessionRepository;

    // =========================================================
    // USER CRUD
    // =========================================================

    /**
     * Create/register a user for IdP services.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody @Valid CreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "User already exists"));
        }

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setDisplayName(request.getFirstName() + " " + request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setDomain(resolveDomainForEmail(email));
        user.setStatus(User.UserStatus.ACTIVE);

        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                user.setRole(User.UserRole.valueOf(request.getRole().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
            }
        }

        if (request.getPreferredMfaMethod() != null && !request.getPreferredMfaMethod().isBlank()) {
            try {
                user.setPreferredMfaMethod(parsePreferredMfaMethod(request.getPreferredMfaMethod()));
                user.setMfaEnabled(true);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid preferredMfaMethod"));
            }
        } else {
            user.setMfaEnabled(Boolean.TRUE.equals(request.getMfaEnabled()));
        }

        User saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(toUserDetail(saved));
    }

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
     * Role changes require SUPER_ADMIN.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates,
            HttpServletRequest httpRequest
    ) {
        // If the request includes a role change, require SUPER_ADMIN
        if (updates.containsKey("role")) {
            String adminRole = (String) httpRequest.getAttribute("adminUserRole");
            if (!"SUPER_ADMIN".equals(adminRole)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only SUPER_ADMIN can change user roles"));
            }
        }
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
     * Delete a user and all their associated records.
     * Requires SUPER_ADMIN role when admin security is enabled.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable String id, HttpServletRequest request) {
        String adminRole = (String) request.getAttribute("adminUserRole");
        if (adminRole != null && !"SUPER_ADMIN".equals(adminRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Clear nullable FK references to preserve audit history
            auditLogRepository.clearUserReference(id);

            // Delete all dependent records before deleting the user
            userConsentRepository.deleteByUserId(id);
            backupCodeRepository.deleteByUserId(id);
            magicLinkRepository.deleteByUserId(id);
            sentOtpRepository.deleteByUserId(id);
            authorizationCodeRepository.deleteByUserId(id);
            tokenRepository.deleteByUserId(id);
            userSessionRepository.deleteByUserId(id);
            totpRepository.deleteByUserId(id);
            webAuthnRepository.deleteByUserId(id);
            userRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(409).build();
        }
    }

    /**
     * Consolidated registration status across OTP email, TOTP, and WebAuthn passkeys.
     */
    @GetMapping("/{id}/auth-registrations")
    public ResponseEntity<Map<String, Object>> getAuthRegistrations(@PathVariable String id) {
        return userRepository.findById(id)
                .map(user -> {
                    List<RegisteredTotp> totpKeys = totpRepository.findByUserIdOrderByUsername(id);
                    List<WebAuthnAuthenticatorEntity> passkeys = webAuthnRepository.findByUserIdOrderByCreatedAtDesc(id);
                    int activeSessionCount = sessionService.findActiveSessionsByUser(user).size();

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("userId", user.getId());
                    response.put("email", user.getEmail());
                    response.put("mfaEnabled", user.isMfaEnabled());
                    response.put("preferredMfaMethod", user.getPreferredMfaMethod() != null ? user.getPreferredMfaMethod().name() : null);

                    Map<String, Object> otp = new LinkedHashMap<>();
                    otp.put("registered", user.getEmail() != null && !user.getEmail().isBlank());
                    otp.put("channel", "EMAIL");
                    otp.put("destination", user.getEmail());

                    Map<String, Object> totp = new LinkedHashMap<>();
                    totp.put("registered", !totpKeys.isEmpty());
                    totp.put("count", totpKeys.size());
                    totp.put("usernames", totpKeys.stream().map(RegisteredTotp::getUsername).collect(Collectors.toList()));

                    Map<String, Object> webauthn = new LinkedHashMap<>();
                    webauthn.put("registered", !passkeys.isEmpty());
                    webauthn.put("count", passkeys.size());
                    webauthn.put("credentials", passkeys.stream().map(p -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", p.getId());
                        m.put("credentialId", p.getCredentialId());
                        m.put("deviceName", p.getDeviceName());
                        m.put("createdAt", p.getCreatedAt());
                        m.put("lastUsedAt", p.getLastUsedAt());
                        return m;
                    }).collect(Collectors.toList()));

                    response.put("otp", otp);
                    response.put("totp", totp);
                    response.put("webauthn", webauthn);
                    response.put("activeSessionCount", activeSessionCount);

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Set preferred MFA method with registration-aware validation.
     */
    @PutMapping("/{id}/preferred-mfa")
    public ResponseEntity<?> setPreferredMfa(
            @PathVariable String id,
            @RequestBody Map<String, Object> request
    ) {
        return userRepository.findById(id)
                .map(user -> {
                    Object rawMethod = request.get("preferredMfaMethod");
                    if (rawMethod == null || rawMethod.toString().isBlank()) {
                        return ResponseEntity.badRequest().body(Map.of("error", "preferredMfaMethod is required"));
                    }

                    final User.MfaMethod method;
                    try {
                        method = parsePreferredMfaMethod(rawMethod.toString());
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Invalid preferredMfaMethod"));
                    }

                    if (method == User.MfaMethod.EMAIL && (user.getEmail() == null || user.getEmail().isBlank())) {
                        return ResponseEntity.badRequest().body(Map.of("error", "User does not have email OTP destination"));
                    }
                    if (method == User.MfaMethod.TOTP && totpRepository.countByUserId(id) == 0) {
                        return ResponseEntity.badRequest().body(Map.of("error", "User does not have registered TOTP key"));
                    }
                    if (method == User.MfaMethod.WEBAUTHN && webAuthnRepository.countByUserId(id) == 0) {
                        return ResponseEntity.badRequest().body(Map.of("error", "User does not have registered WebAuthn passkey"));
                    }

                    user.setPreferredMfaMethod(method);
                    if (request.containsKey("mfaEnabled")) {
                        user.setMfaEnabled(Boolean.parseBoolean(String.valueOf(request.get("mfaEnabled"))));
                    } else {
                        user.setMfaEnabled(true);
                    }

                    User saved = userRepository.save(user);
                    return ResponseEntity.ok(toUserDetail(saved));
                })
                .orElse(ResponseEntity.notFound().build());
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
    // IDP SESSIONS
    // =========================================================

    /**
     * Get active IdP sessions for a user.
     */
    @GetMapping("/{id}/sessions")
    public ResponseEntity<List<Map<String, Object>>> getUserSessions(@PathVariable String id) {
        return userRepository.findById(id)
                .map(user -> {
                    List<Session> sessions = sessionService.findActiveSessionsByUser(user);
                    List<Map<String, Object>> result = sessions.stream().map(s -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("sessionId", s.getSessionId());
                        m.put("ipAddress", s.getIpAddress());
                        m.put("deviceInfo", s.getDeviceInfo());
                        m.put("authMethod", s.getAuthMethod() != null ? s.getAuthMethod().name() : null);
                        m.put("authLevel", s.getAuthLevel());
                        m.put("createdAt", s.getCreatedAt());
                        m.put("lastActivityAt", s.getLastActivityAt());
                        m.put("expiresAt", s.getExpiresAt());
                        m.put("active", s.isActive());
                        return m;
                    }).collect(Collectors.toList());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Revoke a specific active session for a user.
     */
    @PostMapping("/{id}/sessions/{sessionId}/revoke")
    public ResponseEntity<Map<String, Object>> revokeUserSession(
            @PathVariable String id,
            @PathVariable String sessionId
    ) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        boolean belongsToUser = sessionService.findActiveSessionsByUser(userOpt.get()).stream()
                .anyMatch(s -> sessionId.equals(s.getSessionId()));

        if (!belongsToUser) {
            return ResponseEntity.notFound().build();
        }

        sessionService.revokeSession(sessionId, "admin_revoke_session");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "revoked");
        response.put("sessionId", sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Revoke all active sessions for a user.
     */
    @PostMapping("/{id}/sessions/revoke-all")
    public ResponseEntity<Map<String, Object>> revokeAllUserSessions(@PathVariable String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int revokedCount = sessionService.revokeAllUserSessions(userOpt.get(), "admin_revoke_all_sessions");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "revoked_all");
        response.put("revokedCount", revokedCount);
        return ResponseEntity.ok(response);
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

    private Domain resolveDomainForEmail(String email) {
        String domainName = "default.com";
        int at = email.indexOf('@');
        if (at > -1 && at < email.length() - 1) {
            domainName = email.substring(at + 1).toLowerCase(Locale.ROOT);
        }

        String finalDomainName = domainName;
        return domainRepository.findByDomainName(domainName)
                .orElseGet(() -> {
                    Domain domain = new Domain();
                    domain.setDomainName(finalDomainName);
                    domain.setDisplayName(finalDomainName);
                    domain.setOwnerEmail(email);
                    domain.setActive(true);
                    return domainRepository.save(domain);
                });
    }

    private User.MfaMethod parsePreferredMfaMethod(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("OTP".equals(normalized) || "EMAIL_OTP".equals(normalized)) {
            return User.MfaMethod.EMAIL;
        }
        if ("PASSKEY".equals(normalized)) {
            return User.MfaMethod.WEBAUTHN;
        }
        return User.MfaMethod.valueOf(normalized);
    }
}
