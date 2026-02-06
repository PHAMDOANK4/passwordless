package org.openidentityplatform.passwordless.oauth2.repositories;

import org.openidentityplatform.passwordless.oauth2.models.AuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Authorization Code operations
 * Supports OAuth2 authorization code flow
 */
@Repository
public interface AuthorizationCodeRepository extends JpaRepository<AuthorizationCode, UUID> {
    
    /**
     * Find valid (unused and non-expired) authorization code
     */
    Optional<AuthorizationCode> findByCodeAndUsedFalseAndExpiresAtAfter(String code, Instant now);
    
    /**
     * Find authorization code by code value
     */
    Optional<AuthorizationCode> findByCode(String code);
    
    /**
     * Delete expired authorization codes (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM AuthorizationCode ac WHERE ac.expiresAt < :now")
    int deleteExpiredCodes(Instant now);
    
    /**
     * Count active codes for a user (for rate limiting)
     */
    @Query("SELECT COUNT(ac) FROM AuthorizationCode ac WHERE ac.user.id = :userId AND ac.used = false AND ac.expiresAt > :now")
    long countActiveCodesByUser(String userId, Instant now);
}
