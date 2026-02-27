package org.openidentityplatform.passwordless.oauth2.repositories;

import org.openidentityplatform.passwordless.oauth2.models.Session;
import org.openidentityplatform.passwordless.iam.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Session management
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {
    
    /**
     * Find session by session ID
     */
    Optional<Session> findBySessionId(String sessionId);
    
    /**
     * Find active session by session ID
     */
    @Query("SELECT s FROM Session s WHERE s.sessionId = :sessionId " +
           "AND s.revoked = false AND s.expiresAt > :now")
    Optional<Session> findActiveSession(@Param("sessionId") String sessionId, @Param("now") Instant now);
    
    /**
     * Find all active sessions for a user
     */
    @Query("SELECT s FROM Session s WHERE s.user = :user " +
           "AND s.revoked = false AND s.expiresAt > :now ORDER BY s.lastActivityAt DESC")
    List<Session> findActiveSessions(@Param("user") User user, @Param("now") Instant now);
    
    /**
     * Count active sessions for a user
     */
    @Query("SELECT COUNT(s) FROM Session s WHERE s.user = :user " +
           "AND s.revoked = false AND s.expiresAt > :now")
    long countActiveSessions(@Param("user") User user, @Param("now") Instant now);
    
    /**
     * Revoke session
     */
    @Query("UPDATE Session s SET s.revoked = true, s.revokedAt = :now, s.revokedReason = :reason " +
           "WHERE s.sessionId = :sessionId")
    void revokeSession(@Param("sessionId") String sessionId, 
                      @Param("now") Instant now, 
                      @Param("reason") String reason);
    
    /**
     * Revoke all sessions for a user
     */
    @Query("UPDATE Session s SET s.revoked = true, s.revokedAt = :now, s.revokedReason = :reason " +
           "WHERE s.user = :user AND s.revoked = false")
    void revokeAllUserSessions(@Param("user") User user, 
                               @Param("now") Instant now, 
                               @Param("reason") String reason);
    
    /**
     * Delete expired sessions (cleanup job)
     */
    void deleteByExpiresAtBefore(Instant cutoffDate);
    
    /**
     * Find sessions by IP address (for security monitoring)
     */
    List<Session> findByIpAddressAndUserOrderByCreatedAtDesc(String ipAddress, User user);
}
