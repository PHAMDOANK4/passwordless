package org.openidentityplatform.passwordless.oauth2.repositories;

import org.openidentityplatform.passwordless.oauth2.models.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConsentRepository extends JpaRepository<UserConsent, String> {

    Optional<UserConsent> findByUserIdAndClientId(String userId, String clientId);

    void deleteByUserIdAndClientId(String userId, String clientId);

    List<UserConsent> findByUserId(String userId);
}
