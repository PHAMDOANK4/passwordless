package org.openidentityplatform.passwordless.webauthn.repositories;

import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
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
    private final org.openidentityplatform.passwordless.iam.repositories.UserRepository userRepository;
    private final org.openidentityplatform.passwordless.iam.repositories.DomainRepository domainRepository;
    
    @Override
    public void save(String username, CredentialRecord credentialRecord) {
        AuthenticatorEntity authenticatorEntity = AuthenticatorEntity.fromCredentialRecord(credentialRecord);
        
        WebAuthnAuthenticatorEntity webAuthnAuthenticatorEntity = new WebAuthnAuthenticatorEntity();
        webAuthnAuthenticatorEntity.setUsername(username);
        webAuthnAuthenticatorEntity.setAuthenticator(authenticatorEntity.toJson());
        
        // --- JIT PROVISIONING TO SUPPORT IAM ADMIN DASHBOARD ---
        // Create default domain if not exists
        org.openidentityplatform.passwordless.iam.models.Domain defaultDomain = domainRepository.findByDomainName("default.com")
                .orElseGet(() -> {
                    org.openidentityplatform.passwordless.iam.models.Domain d = new org.openidentityplatform.passwordless.iam.models.Domain();
                    d.setDomainName("default.com");
                    d.setDisplayName("Default Domain");
                    d.setOwnerEmail("admin@default.com");
                    return domainRepository.save(d);
                });
                
        // Auto-create IAM User if not exists
        org.openidentityplatform.passwordless.iam.models.User iamUser = userRepository.findByEmail(username)
                .orElseGet(() -> {
                    org.openidentityplatform.passwordless.iam.models.User u = new org.openidentityplatform.passwordless.iam.models.User();
                    u.setEmail(username);
                    u.setDomain(defaultDomain);
                    u.setDisplayName(username);
                    u.setRole(org.openidentityplatform.passwordless.iam.models.User.UserRole.USER);
                    return userRepository.save(u);
                });
        webAuthnAuthenticatorEntity.setUser(iamUser);
        // -------------------------------------------------------
        
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
                    // Load credential record from JSON
                    CredentialRecord credentialRecord = AuthenticatorEntity.fromJson(wa.getAuthenticator()).toCredentialRecord();
                    
                    // IMPORTANT: Override the counter from JSON with the database column value
                    // This ensures we always use the most up-to-date counter value
                    // The JSON may have stale counter values from previous authentications
                    Long dbCounter = wa.getCounter();
                    if (dbCounter != null && dbCounter != credentialRecord.getCounter()) {
                        // Create new CredentialRecord with database counter value
                        credentialRecord = new CredentialRecordImpl(
                            credentialRecord.getAttestationStatement(),
                            credentialRecord.isUvInitialized(),
                            credentialRecord.isBackupEligible(),
                            credentialRecord.isBackedUp(),
                            dbCounter, // Use counter from database column
                            credentialRecord.getAttestedCredentialData(),
                            credentialRecord.getAuthenticatorExtensions(),
                            credentialRecord.getClientData(),
                            credentialRecord.getClientExtensions(),
                            credentialRecord.getTransports()
                        );
                    }
                    
                    return credentialRecord;
                })
                .collect(Collectors.toSet());
    }
    
    public void updateCounter(String username, byte[] credentialId, long counter) {
        String credentialIdStr = Base64.getEncoder().encodeToString(credentialId);
        List<WebAuthnAuthenticatorEntity> authenticators = userAuthenticatorJPARepository.getAllByUsername(username);
        
        for (WebAuthnAuthenticatorEntity entity : authenticators) {
            if (credentialIdStr.equals(entity.getCredentialId())) {
                // Update counter and last used timestamp
                entity.setCounter(counter);
                entity.setLastUsedAt(Instant.now());
                
                // Also update the counter in the stored JSON
                CredentialRecord credentialRecord = AuthenticatorEntity.fromJson(entity.getAuthenticator()).toCredentialRecord();
                // Create a new CredentialRecord with updated counter
                CredentialRecord updatedRecord = new CredentialRecordImpl(
                    credentialRecord.getAttestationStatement(),
                    credentialRecord.isUvInitialized(),
                    credentialRecord.isBackupEligible(),
                    credentialRecord.isBackedUp(),
                    counter,
                    credentialRecord.getAttestedCredentialData(),
                    credentialRecord.getAuthenticatorExtensions(),
                    credentialRecord.getClientData(),
                    credentialRecord.getClientExtensions(),
                    credentialRecord.getTransports()
                );
                
                // Save updated authenticator JSON
                AuthenticatorEntity updatedAuthEntity = AuthenticatorEntity.fromCredentialRecord(updatedRecord);
                entity.setAuthenticator(updatedAuthEntity.toJson());
                
                userAuthenticatorJPARepository.save(entity);
                break;
            }
        }
    }
}
