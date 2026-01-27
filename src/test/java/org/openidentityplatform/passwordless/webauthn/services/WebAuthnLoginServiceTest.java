package org.openidentityplatform.passwordless.webauthn.services;

import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.client.CollectedClientData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.webauthn.configuration.WebAuthnConfiguration;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorRDBMSRepository;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebAuthn Login Service
 * Tests the authentication flow including challenge generation and credential verification
 */
class WebAuthnLoginServiceTest {

    private WebAuthnConfiguration webAuthnConfiguration;
    private UserAuthenticatorRDBMSRepository userAuthenticatorRepository;
    private WebAuthnLoginService webAuthnLoginService;
    private MockHttpServletRequest request;

    @BeforeEach
    void setup() {
        webAuthnConfiguration = mock(WebAuthnConfiguration.class);
        userAuthenticatorRepository = mock(UserAuthenticatorRDBMSRepository.class);
        
        // Configure mock with typical WebAuthn settings
        when(webAuthnConfiguration.getRpId()).thenReturn("localhost");
        when(webAuthnConfiguration.getOriginUrl()).thenReturn("http://localhost:8080");
        when(webAuthnConfiguration.getTimeout()).thenReturn(60000L);
        
        webAuthnLoginService = new WebAuthnLoginService(webAuthnConfiguration, userAuthenticatorRepository);
        
        // Create request with a session
        request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("id", UUID.randomUUID().toString());
        request.setSession(session);
    }

    @Test
    void testRequestCredentials_WithEmptyAuthenticators() {
        // Given
        String username = "testuser@example.com";
        Set<CredentialRecord> emptyAuthenticators = new HashSet<>();
        
        // When
        PublicKeyCredentialRequestOptions options = webAuthnLoginService.requestCredentials(
            username, request, emptyAuthenticators
        );
        
        // Then
        assertNotNull(options, "Options should not be null");
        assertNotNull(options.getChallenge(), "Challenge should be generated");
        assertEquals("localhost", options.getRpId(), "RP ID should match configuration");
        assertEquals(60000L, options.getTimeout(), "Timeout should match configuration");
        assertNotNull(options.getUserVerification(), "User verification should be set");
    }

    @Test
    void testRequestCredentials_WithExistingAuthenticators() {
        // Given
        String username = "testuser@example.com";
        Set<CredentialRecord> authenticators = createMockAuthenticators();
        
        // When
        PublicKeyCredentialRequestOptions options = webAuthnLoginService.requestCredentials(
            username, request, authenticators
        );
        
        // Then
        assertNotNull(options, "Options should not be null");
        assertNotNull(options.getChallenge(), "Challenge should be generated");
        assertTrue(options.getChallenge().getValue().length > 0, "Challenge should have value");
        assertEquals("localhost", options.getRpId(), "RP ID should match configuration");
    }

    @Test
    void testRequestCredentials_ChallengeBasedOnSession() {
        // Given
        String username = "testuser@example.com";
        Set<CredentialRecord> authenticators = new HashSet<>();
        
        // When
        PublicKeyCredentialRequestOptions options1 = webAuthnLoginService.requestCredentials(
            username, request, authenticators
        );
        
        // Create new request with different session
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpSession session2 = new MockHttpSession();
        session2.setAttribute("id", UUID.randomUUID().toString());
        request2.setSession(session2);
        
        PublicKeyCredentialRequestOptions options2 = webAuthnLoginService.requestCredentials(
            username, request2, authenticators
        );
        
        // Then
        assertNotNull(options1.getChallenge(), "First challenge should exist");
        assertNotNull(options2.getChallenge(), "Second challenge should exist");
        // Challenges should be different as they're based on different sessions
        assertNotEquals(
            new String(options1.getChallenge().getValue()),
            new String(options2.getChallenge().getValue()),
            "Challenges from different sessions should be different"
        );
    }

    @Test
    void testRequestCredentials_UserVerificationPreferred() {
        // Given
        String username = "testuser@example.com";
        Set<CredentialRecord> authenticators = new HashSet<>();
        
        // When
        PublicKeyCredentialRequestOptions options = webAuthnLoginService.requestCredentials(
            username, request, authenticators
        );
        
        // Then
        assertNotNull(options.getUserVerification(), "User verification requirement should be set");
        assertEquals("preferred", options.getUserVerification().getValue(), 
            "User verification should be PREFERRED");
    }

    @Test
    void testRequestCredentials_WithNullUsername() {
        // Given
        String username = null;
        Set<CredentialRecord> authenticators = new HashSet<>();
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            PublicKeyCredentialRequestOptions options = webAuthnLoginService.requestCredentials(
                username, request, authenticators
            );
            assertNotNull(options, "Options should still be created with null username");
        });
    }

    @Test
    void testRequestCredentials_WithMultipleAuthenticators() {
        // Given
        String username = "testuser@example.com";
        Set<CredentialRecord> authenticators = createMultipleMockAuthenticators(3);
        
        // When
        PublicKeyCredentialRequestOptions options = webAuthnLoginService.requestCredentials(
            username, request, authenticators
        );
        
        // Then
        assertNotNull(options, "Options should not be null");
        assertNotNull(options.getChallenge(), "Challenge should be generated");
        // The service should handle multiple authenticators without issues
    }

    /**
     * Helper method to create mock authenticators for testing
     */
    private Set<CredentialRecord> createMockAuthenticators() {
        Set<CredentialRecord> authenticators = new HashSet<>();
        
        // Create a mock credential record
        AttestedCredentialData attestedCredentialData = mock(AttestedCredentialData.class);
        when(attestedCredentialData.getCredentialId()).thenReturn("test-credential-id".getBytes());
        when(attestedCredentialData.getCOSEKey()).thenReturn(mock(COSEKey.class));
        
        AttestationStatement attestationStatement = new NoneAttestationStatement();
        CollectedClientData collectedClientData = mock(CollectedClientData.class);
        
        // Use correct constructor: attestationStatement, uvInitialized, backupEligible, backedUp, counter,
        // attestedCredentialData, authenticatorExtensions, clientData, clientExtensions, transports
        CredentialRecord credentialRecord = new CredentialRecordImpl(
            attestationStatement,
            false, // uvInitialized
            false, // backupEligible
            false, // backedUp
            0L, // counter
            attestedCredentialData,
            null, // authenticatorExtensions
            collectedClientData,
            null, // clientExtensions
            Collections.emptySet() // transports
        );
        
        authenticators.add(credentialRecord);
        return authenticators;
    }

    /**
     * Helper method to create multiple mock authenticators
     */
    private Set<CredentialRecord> createMultipleMockAuthenticators(int count) {
        Set<CredentialRecord> authenticators = new HashSet<>();
        
        for (int i = 0; i < count; i++) {
            AttestedCredentialData attestedCredentialData = mock(AttestedCredentialData.class);
            when(attestedCredentialData.getCredentialId()).thenReturn(("credential-" + i).getBytes());
            when(attestedCredentialData.getCOSEKey()).thenReturn(mock(COSEKey.class));
            
            AttestationStatement attestationStatement = new NoneAttestationStatement();
            CollectedClientData collectedClientData = mock(CollectedClientData.class);
            
            CredentialRecord credentialRecord = new CredentialRecordImpl(
                attestationStatement,
                false, // uvInitialized
                false, // backupEligible
                false, // backedUp  
                (long) i, // counter - different for each
                attestedCredentialData,
                null, // authenticatorExtensions
                collectedClientData,
                null, // clientExtensions
                Collections.emptySet() // transports
            );
            
            authenticators.add(credentialRecord);
        }
        
        return authenticators;
    }
}
