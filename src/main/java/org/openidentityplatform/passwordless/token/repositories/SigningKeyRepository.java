package org.openidentityplatform.passwordless.token.repositories;

import org.openidentityplatform.passwordless.token.models.SigningKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SigningKeyRepository extends JpaRepository<SigningKey, String> {

    Optional<SigningKey> findByKid(String kid);

    List<SigningKey> findByStatus(SigningKey.KeyStatus status);

    Optional<SigningKey> findFirstByStatusOrderByCreatedAtDesc(SigningKey.KeyStatus status);
}
