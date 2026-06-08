package org.openidentityplatform.passwordless.otp.repositories;

import org.openidentityplatform.passwordless.otp.models.SentOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SentOtpRepository extends JpaRepository<SentOtp, UUID> {
    Optional<SentOtp> findFirstByDestinationOrderByLastSentAtDesc(String destination);
    List<SentOtp> findByDestinationAndOtpOrderByLastSentAtDesc(String destination, String otp);

    // Admin queries
    List<SentOtp> findByDestinationOrderByLastSentAtDesc(String destination);
    long count();

    @Modifying
    @Query("DELETE FROM SentOtp s WHERE s.user.id = :userId")
    int deleteByUserId(@Param("userId") String userId);
}

