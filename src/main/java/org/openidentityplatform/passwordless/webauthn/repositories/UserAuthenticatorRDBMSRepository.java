package org.openidentityplatform.passwordless.webauthn.repositories;

import com.webauthn4j.credential.CredentialRecord;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository("userAuthenticatorRepository")
@AllArgsConstructor
public class UserAuthenticatorRDBMSRepository implements UserAuthenticatorRepository {

    private final UserAuthenticatorJPARepository userAuthenticatorJPARepository;
    
    @Override
    public void save(String username, CredentialRecord credentialRecord) {
        AuthenticatorEntity authenticatorEntity = AuthenticatorEntity.fromCredentialRecord(credentialRecord);
        
        WebAuthnAuthenticatorEntity webAuthnAuthenticatorEntity = new WebAuthnAuthenticatorEntity();
        webAuthnAuthenticatorEntity.setUsername(username);
        webAuthnAuthenticatorEntity.setAuthenticator(authenticatorEntity.toJson());
        
        // Extract credential ID from the credential record
        if (credentialRecord.getAttestedCredentialData() != null) {
            byte[] credentialId = credentialRecord.getAttestedCredentialData().getCredentialId();
            webAuthnAuthenticatorEntity.setCredentialId(Base64.getEncoder().encodeToString(credentialId));
        }
        
        // Store metadata for easier querying and display
        webAuthnAuthenticatorEntity.setCounter(credentialRecord.getCounter());
        webAuthnAuthenticatorEntity.setBackupEligible(credentialRecord.isBackupEligible());
        webAuthnAuthenticatorEntity.setBackedUp(credentialRecord.isBackedUp());
        webAuthnAuthenticatorEntity.setUvInitialized(credentialRecord.isUvInitialized());
        
        // Store attestation format
        if (credentialRecord.getAttestationStatement() != null) {
            webAuthnAuthenticatorEntity.setAttestationType(credentialRecord.getAttestationStatement().getFormat());
        }
        
        // Store transports as comma-separated string
        if (credentialRecord.getTransports() != null && !credentialRecord.getTransports().isEmpty()) {
            String transports = credentialRecord.getTransports().stream()
                    .map(transport -> transport.getValue())
                    .collect(Collectors.joining(","));
            webAuthnAuthenticatorEntity.setTransports(transports);
        }
        
        webAuthnAuthenticatorEntity.setLastUsedAt(Instant.now());
        
        userAuthenticatorJPARepository.save(webAuthnAuthenticatorEntity);
    }

    @Override
    public Set<CredentialRecord> load(String username) {
        List<WebAuthnAuthenticatorEntity> webAuthenticators = userAuthenticatorJPARepository.getAllByUsername(username);
        return webAuthenticators.stream()
                .map(wa -> {
                    CredentialRecord credentialRecord = AuthenticatorEntity.fromJson(wa.getAuthenticator()).toCredentialRecord();
                    // Update last used timestamp
                    wa.setLastUsedAt(Instant.now());
                    wa.setCounter(credentialRecord.getCounter());
                    userAuthenticatorJPARepository.save(wa);
                    return credentialRecord;
                })
                .collect(Collectors.toSet());
    }
}
