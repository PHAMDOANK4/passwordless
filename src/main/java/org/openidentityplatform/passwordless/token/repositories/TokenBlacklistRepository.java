package org.openidentityplatform.passwordless.token.repositories;

import org.openidentityplatform.passwordless.token.models.TokenBlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklistEntry, UUID> {

    boolean existsByJti(String jti);

    void deleteByExpiresAtBefore(Instant cutoff);
}
