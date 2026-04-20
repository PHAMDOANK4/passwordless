package org.openidentityplatform.passwordless.auth.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.openidentityplatform.passwordless.configuration.SecurityProperties;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Log4j2
public class AccountLockoutService {

    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;

    public void checkLockout(User user) {
        if (user.isLocked()) {
            log.warn("Account locked for user {}", user.getEmail());
            throw new AccountLockedException("Account is locked until " + user.getLockedUntil());
        }
    }

    @Transactional
    public void recordFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= securityProperties.getMaxFailedAttempts()) {
            user.setLockedUntil(Instant.now().plus(securityProperties.getLockoutDurationMinutes(), ChronoUnit.MINUTES));
            log.warn("Account locked for user {} after {} failed attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);
    }

    @Transactional
    public void resetFailedAttempts(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}
