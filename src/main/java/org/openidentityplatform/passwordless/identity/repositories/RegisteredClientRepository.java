package org.openidentityplatform.passwordless.identity.repositories;

import org.openidentityplatform.passwordless.identity.models.RegisteredClient;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegisteredClientRepository extends CrudRepository<RegisteredClient, UUID> {

    Optional<RegisteredClient> findByAppKey(String appKey);
}
