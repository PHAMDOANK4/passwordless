package org.openidentityplatform.passwordless.oauth2.repositories;

import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OAuth2 Client management
 */
@Repository
public interface OAuthClientRepository extends JpaRepository<OAuthClient, UUID> {
    
    /**
     * Find OAuth client by client ID
     */
    Optional<OAuthClient> findByClientId(String clientId);
    
    /**
     * Find all active clients for a domain
     */
    @Query("SELECT c FROM OAuthClient c WHERE c.domain = :domain AND c.active = true")
    List<OAuthClient> findActiveClientsByDomain(@Param("domain") Domain domain);
    
    /**
     * Find all clients for a domain
     */
    List<OAuthClient> findByDomain(Domain domain);
    
    /**
     * Check if client ID exists
     */
    boolean existsByClientId(String clientId);
    
    /**
     * Find client by ID and domain (for authorization)
     */
    @Query("SELECT c FROM OAuthClient c WHERE c.clientId = :clientId AND c.domain = :domain AND c.active = true")
    Optional<OAuthClient> findActiveClientByIdAndDomain(@Param("clientId") String clientId, 
                                                         @Param("domain") Domain domain);
}
