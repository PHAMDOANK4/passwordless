package org.openidentityplatform.passwordless.webauthn.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAuthenticatorJPARepository extends JpaRepository<WebAuthnAuthenticatorEntity, Long> {
    List<WebAuthnAuthenticatorEntity> getAllByUsername(String username);
    Optional<WebAuthnAuthenticatorEntity> findByCredentialId(String credentialId);
    Optional<WebAuthnAuthenticatorEntity> findByUsernameAndCredentialId(String username, String credentialId);
}
