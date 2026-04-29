package org.openidentityplatform.passwordless.recovery.repositories;

import org.openidentityplatform.passwordless.recovery.models.BackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BackupCodeRepository extends JpaRepository<BackupCode, String> {

    List<BackupCode> findByUserIdAndUsedFalse(String userId);

    long countByUserIdAndUsedFalse(String userId);

    void deleteByUserId(String userId);
}
