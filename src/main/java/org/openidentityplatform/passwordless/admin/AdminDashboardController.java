package org.openidentityplatform.passwordless.admin;

import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.apps.repositories.AuditLogRepository;
import org.openidentityplatform.passwordless.apps.repositories.RegisteredAppRepository;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.otp.repositories.SentOtpRepository;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorJPARepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin Dashboard Stats API - Read-only, does NOT touch any business logic.
 */
@RestController
@RequestMapping("/admin/api/dashboard")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final RegisteredTotpRepository totpRepository;
    private final UserAuthenticatorJPARepository webAuthnRepository;
    private final SentOtpRepository sentOtpRepository;
    private final RegisteredAppRepository appRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // User counts
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(User.UserStatus.ACTIVE);
        long suspendedUsers = userRepository.countByStatus(User.UserStatus.SUSPENDED);
        long mfaEnabledUsers = userRepository.countByMfaEnabledTrue();

        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("suspendedUsers", suspendedUsers);
        stats.put("mfaEnabledUsers", mfaEnabledUsers);

        // Auth keys
        long totalTotpKeys = totpRepository.count();
        long totalPasskeys = webAuthnRepository.count();
        long totalOtpSessions = sentOtpRepository.count();

        stats.put("totalTotpKeys", totalTotpKeys);
        stats.put("totalPasskeys", totalPasskeys);
        stats.put("totalOtpSessions", totalOtpSessions);

        // Apps
        long totalApps = appRepository.count();
        stats.put("totalApps", totalApps);

        // Audit logs
        long totalAuditLogs = auditLogRepository.count();
        stats.put("totalAuditLogs", totalAuditLogs);

        return ResponseEntity.ok(stats);
    }
}
