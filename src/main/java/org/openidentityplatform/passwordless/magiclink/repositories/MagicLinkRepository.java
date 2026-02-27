package org.openidentityplatform.passwordless.magiclink.repositories;

import org.openidentityplatform.passwordless.magiclink.models.MagicLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Magic Link management
 */
@Repository
public interface MagicLinkRepository extends JpaRepository<MagicLink, UUID> {
    
    /**
     * Find magic link by token
     */
    Optional<MagicLink> findByToken(String token);
    
    /**
     * Find valid magic link by token
     */
    @Query("SELECT m FROM MagicLink m WHERE m.token = :token " +
           "AND m.used = false AND m.expiresAt > :now AND m.attempts < m.maxAttempts")
    Optional<MagicLink> findValidMagicLink(@Param("token") String token, @Param("now") Instant now);
    
    /**
     * Find all magic links for an email
     */
    List<MagicLink> findByEmailOrderByCreatedAtDesc(String email);
    
    /**
     * Find recent unused links for an email (rate limiting)
     */
    @Query("SELECT m FROM MagicLink m WHERE m.email = :email " +
           "AND m.used = false AND m.createdAt > :since")
    List<MagicLink> findRecentUnusedLinks(@Param("email") String email, @Param("since") Instant since);
    
    /**
     * Delete expired magic links (cleanup job)
     */
    void deleteByExpiresAtBefore(Instant cutoffDate);
    
    /**
     * Count magic links created recently for rate limiting
     */
    @Query("SELECT COUNT(m) FROM MagicLink m WHERE m.email = :email AND m.createdAt > :since")
    long countRecentLinks(@Param("email") String email, @Param("since") Instant since);
}
