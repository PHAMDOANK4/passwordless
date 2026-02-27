package org.openidentityplatform.passwordless.auth.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    private final UserRepository userRepository;

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
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES));
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
