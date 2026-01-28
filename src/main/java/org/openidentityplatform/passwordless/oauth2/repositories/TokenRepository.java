package org.openidentityplatform.passwordless.oauth2.repositories;

import org.openidentityplatform.passwordless.oauth2.models.Token;
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
 * Repository for OAuth2 Token management
 */
@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {
    
    /**
     * Find a valid token by value
     */
    @Query("SELECT t FROM Token t WHERE t.tokenValue = :tokenValue AND t.revoked = false")
    Optional<Token> findByTokenValueAndNotRevoked(@Param("tokenValue") String tokenValue);
    
    /**
     * Find all active tokens for a user
     */
    @Query("SELECT t FROM Token t WHERE t.user = :user AND t.revoked = false AND t.expiresAt > :now")
    List<Token> findActiveTokensByUser(@Param("user") User user, @Param("now") Instant now);
    
    /**
     * Find all tokens of a specific type for a user
     */
    List<Token> findByUserAndTokenType(User user, Token.TokenType tokenType);
    
    /**
     * Find refresh token by client and user
     */
    @Query("SELECT t FROM Token t WHERE t.user = :user AND t.clientId = :clientId " +
           "AND t.tokenType = 'REFRESH' AND t.revoked = false AND t.expiresAt > :now")
    Optional<Token> findActiveRefreshToken(@Param("user") User user, 
                                          @Param("clientId") String clientId,
                                          @Param("now") Instant now);
    
    /**
     * Revoke all tokens for a user
     */
    @Query("UPDATE Token t SET t.revoked = true, t.revokedAt = :now WHERE t.user = :user AND t.revoked = false")
    void revokeAllUserTokens(@Param("user") User user, @Param("now") Instant now);
    
    /**
     * Delete expired tokens (cleanup job)
     */
    void deleteByExpiresAtBefore(Instant cutoffDate);
    
    /**
     * Count active sessions (unique tokens) for a user
     */
    @Query("SELECT COUNT(DISTINCT t.clientId) FROM Token t WHERE t.user = :user " +
           "AND t.tokenType = 'ACCESS' AND t.revoked = false AND t.expiresAt > :now")
    long countActiveSessionsByUser(@Param("user") User user, @Param("now") Instant now);
}
