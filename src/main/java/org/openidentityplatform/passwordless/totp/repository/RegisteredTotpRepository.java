package org.openidentityplatform.passwordless.totp.repository;

import org.openidentityplatform.passwordless.totp.models.RegisteredTotp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegisteredTotpRepository extends JpaRepository<RegisteredTotp, String> {

    Optional<RegisteredTotp> findByUsername(String username);

    List<RegisteredTotp> findByUserIdOrderByUsername(String userId);

    long countByUserId(String userId);
}

