package org.openidentityplatform.passwordless.identity.repositories;

import org.openidentityplatform.passwordless.identity.models.IssuedAccessGrant;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IssuedAccessGrantRepository extends CrudRepository<IssuedAccessGrant, UUID> {

    Optional<IssuedAccessGrant> findByBearerToken(String bearerToken);
}
