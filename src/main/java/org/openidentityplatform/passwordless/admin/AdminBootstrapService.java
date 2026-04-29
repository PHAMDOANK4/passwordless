package org.openidentityplatform.passwordless.admin;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bootstraps a default SUPER_ADMIN user on first startup if none exists.
 * <p>
 * The admin email is configurable via environment variable {@code ADMIN_EMAIL}
 * or the {@code admin.default-email} property.
 * <p>
 * Since this is a passwordless system, the admin logs in via OTP/TOTP/WebAuthn
 * —
 * no password is stored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapService {

    private final UserRepository userRepository;
    private final DomainRepository domainRepository;

    @Value("${admin.default-email:admin@system.local}")
    private String defaultAdminEmail;

    @Value("${admin.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    @PostConstruct
    public void initialize() {
        if (!bootstrapEnabled) {
            log.info("Admin bootstrap is disabled");
            return;
        }

        // Check if any SUPER_ADMIN exists
        boolean superAdminExists = userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() == User.UserRole.SUPER_ADMIN);

        if (superAdminExists) {
            log.info("SUPER_ADMIN user already exists, skipping bootstrap");
            return;
        }

        // Check if the default admin email is already registered (just needs role
        // upgrade)
        var existingUser = userRepository.findByEmail(defaultAdminEmail);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setRole(User.UserRole.SUPER_ADMIN);
            userRepository.save(user);
            log.info("Existing user {} promoted to SUPER_ADMIN", defaultAdminEmail);
            return;
        }

        // Create new SUPER_ADMIN user
        Domain systemDomain = domainRepository.findByDomainName("system.local")
                .orElseGet(() -> {
                    Domain domain = new Domain();
                    domain.setDomainName("system.local");
                    domain.setDisplayName("System Domain");
                    domain.setOwnerEmail(defaultAdminEmail);
                    domain.setActive(true);
                    return domainRepository.save(domain);
                });

        User admin = new User();
        admin.setEmail(defaultAdminEmail);
        admin.setDisplayName("System Administrator");
        admin.setFirstName("System");
        admin.setLastName("Administrator");
        admin.setDomain(systemDomain);
        admin.setRole(User.UserRole.SUPER_ADMIN);
        admin.setStatus(User.UserStatus.ACTIVE);
        admin.setMfaEnabled(false); // Admin will set up MFA on first login
        userRepository.save(admin);

        log.info("====================================================");
        log.info("  DEFAULT SUPER_ADMIN CREATED");
        log.info("  Email: {}", defaultAdminEmail);
        log.info("  Login via OTP to access admin panel");
        log.info("====================================================");
    }
}
