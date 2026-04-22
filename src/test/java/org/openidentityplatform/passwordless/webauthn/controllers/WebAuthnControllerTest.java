package org.openidentityplatform.passwordless.webauthn.controllers;

import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.data.AuthenticatorAttachment;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.UserVerificationRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.webauthn.models.AssertRequest;
import org.openidentityplatform.passwordless.webauthn.models.CredentialRequest;
import org.openidentityplatform.passwordless.webauthn.models.WebAuthnCeremonyState;
import org.openidentityplatform.passwordless.webauthn.models.WebAuthnLoginBeginRequest;
import org.openidentityplatform.passwordless.webauthn.models.WebAuthnLoginBeginResponse;
import org.openidentityplatform.passwordless.webauthn.models.WebAuthnLoginFinishRequest;
import org.openidentityplatform.passwordless.webauthn.models.WebAuthnRegisterBeginRequest;
import org.openidentityplatform.passwordless.webauthn.models.WebAuthnRegisterBeginResponse;
import org.openidentityplatform.passwordless.webauthn.models.WebAuthnRegisterFinishRequest;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorRepository;
import org.openidentityplatform.passwordless.webauthn.services.WebAuthnCeremonyService;
import org.openidentityplatform.passwordless.webauthn.services.WebAuthnLoginService;
import org.openidentityplatform.passwordless.webauthn.services.WebAuthnRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebAuthnControllerTest {

    @Mock
    private WebAuthnRegistrationService webAuthnRegistrationService;
    @Mock
    private WebAuthnLoginService webAuthnLoginService;
    @Mock
    private UserAuthenticatorRepository userAuthenticatorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WebAuthnCeremonyService webAuthnCeremonyService;

    private WebAuthnController controller;

    @BeforeEach
    void setUp() {
        controller = new WebAuthnController(
                webAuthnRegistrationService,
                webAuthnLoginService,
                userAuthenticatorRepository,
                userRepository,
                webAuthnCeremonyService
        );
    }

    @Test
    void registerBegin_returnsNotFoundWhenUserMissing() {
        WebAuthnRegisterBeginRequest request = new WebAuthnRegisterBeginRequest();
        request.setUsername("missing@example.com");

        when(userRepository.existsByEmail("missing@example.com")).thenReturn(false);

        ResponseEntity<?> response = controller.registerBegin(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("User not found. Register user first.", body.get("error"));
    }

    @Test
    void registerBegin_createsTransactionForUsbKey() {
        WebAuthnRegisterBeginRequest request = new WebAuthnRegisterBeginRequest();
        request.setUsername("Alice@Example.com");
        request.setAuthenticatorAttachment("usb");
        request.setResidentKeyRequired(false);
        request.setUserVerification("preferred");

        byte[] challenge = new byte[]{1, 2, 3};
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);
        when(webAuthnCeremonyService.generateChallenge()).thenReturn(challenge);
        when(webAuthnCeremonyService.create(
                "alice@example.com",
                WebAuthnCeremonyState.CeremonyType.REGISTRATION,
                challenge
        )).thenReturn("tx-reg-1");
        when(webAuthnRegistrationService.requestCredentials(
                eq("alice@example.com"),
                eq(challenge),
                eq(AuthenticatorAttachment.CROSS_PLATFORM),
                eq(false),
                eq(UserVerificationRequirement.PREFERRED)
        )).thenReturn(null);

        ResponseEntity<?> response = controller.registerBegin(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        WebAuthnRegisterBeginResponse body = assertInstanceOf(WebAuthnRegisterBeginResponse.class, response.getBody());
        assertEquals("tx-reg-1", body.getTransactionId());
    }

    @Test
    void registerFinish_returnsBadRequestWhenTransactionMissing() {
        WebAuthnRegisterFinishRequest request = new WebAuthnRegisterFinishRequest();
        request.setTransactionId("missing-tx");
        request.setCredential(new CredentialRequest());

        when(webAuthnCeremonyService.findByType("missing-tx", WebAuthnCeremonyState.CeremonyType.REGISTRATION))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.registerFinish(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("Registration transaction is invalid or expired", body.get("error"));
    }

    @Test
    void registerFinish_savesCredentialAndDeletesTransaction() {
        WebAuthnCeremonyState state = new WebAuthnCeremonyState();
        state.setUsername("alice@example.com");

        WebAuthnRegisterFinishRequest request = new WebAuthnRegisterFinishRequest();
        request.setTransactionId("tx-reg-2");
        CredentialRequest credentialRequest = new CredentialRequest();
        request.setCredential(credentialRequest);

        byte[] challenge = new byte[]{9, 8, 7};
        CredentialRecord credentialRecord = mock(CredentialRecord.class, RETURNS_DEEP_STUBS);
        when(credentialRecord.getAttestedCredentialData().getCredentialId()).thenReturn(new byte[]{3, 2, 1});

        when(webAuthnCeremonyService.findByType("tx-reg-2", WebAuthnCeremonyState.CeremonyType.REGISTRATION))
                .thenReturn(Optional.of(state));
        when(webAuthnCeremonyService.decodeChallenge(state)).thenReturn(challenge);
        when(webAuthnRegistrationService.processCredentials(eq(credentialRequest), eq(challenge))).thenReturn(credentialRecord);

        ResponseEntity<?> response = controller.registerFinish(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("registered", body.get("status"));
        assertEquals("alice@example.com", body.get("username"));
        assertEquals(Base64.getUrlEncoder().encodeToString(new byte[]{3, 2, 1}), body.get("credentialId"));
        verify(userAuthenticatorRepository).save("alice@example.com", credentialRecord);
        verify(webAuthnCeremonyService).delete("tx-reg-2");
    }

    @Test
    void loginBegin_returnsNotFoundWhenNoCredentialRegistered() {
        WebAuthnLoginBeginRequest request = new WebAuthnLoginBeginRequest();
        request.setUsername("nobody@example.com");

        when(userAuthenticatorRepository.load("nobody@example.com")).thenReturn(Set.of());

        ResponseEntity<?> response = controller.loginBegin(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("No FIDO2 credentials registered for this user", body.get("error"));
    }

    @Test
    void loginBegin_returnsTransactionWhenCredentialExists() {
        WebAuthnLoginBeginRequest request = new WebAuthnLoginBeginRequest();
        request.setUsername("user@example.com");

        Set<CredentialRecord> authenticators = Set.of(mock(CredentialRecord.class));
        byte[] challenge = new byte[]{4, 5, 6};

        when(userAuthenticatorRepository.load("user@example.com")).thenReturn(authenticators);
        when(webAuthnCeremonyService.generateChallenge()).thenReturn(challenge);
        when(webAuthnCeremonyService.create(
                "user@example.com",
                WebAuthnCeremonyState.CeremonyType.AUTHENTICATION,
                challenge
        )).thenReturn("tx-login-1");
        when(webAuthnLoginService.requestCredentials("user@example.com", challenge, authenticators))
                .thenReturn(mock(PublicKeyCredentialRequestOptions.class));

        ResponseEntity<?> response = controller.loginBegin(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        WebAuthnLoginBeginResponse body = assertInstanceOf(WebAuthnLoginBeginResponse.class, response.getBody());
        assertEquals("tx-login-1", body.getTransactionId());
    }

    @Test
    void loginFinish_returnsBadRequestWhenTransactionMissing() {
        WebAuthnLoginFinishRequest request = new WebAuthnLoginFinishRequest();
        request.setTransactionId("missing-login-tx");
        request.setAssertion(new AssertRequest());

        when(webAuthnCeremonyService.findByType("missing-login-tx", WebAuthnCeremonyState.CeremonyType.AUTHENTICATION))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.loginFinish(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("Login transaction is invalid or expired", body.get("error"));
    }

    @Test
    void loginFinish_returnsVerifiedAndDeletesTransaction() {
        WebAuthnCeremonyState state = new WebAuthnCeremonyState();
        state.setUsername("user@example.com");

        WebAuthnLoginFinishRequest request = new WebAuthnLoginFinishRequest();
        request.setTransactionId("tx-login-2");
        AssertRequest assertRequest = new AssertRequest();
        request.setAssertion(assertRequest);

        Set<CredentialRecord> authenticators = Set.of(mock(CredentialRecord.class));
        byte[] challenge = new byte[]{7, 7, 7};

        when(webAuthnCeremonyService.findByType("tx-login-2", WebAuthnCeremonyState.CeremonyType.AUTHENTICATION))
                .thenReturn(Optional.of(state));
        when(userAuthenticatorRepository.load("user@example.com")).thenReturn(authenticators);
        when(webAuthnCeremonyService.decodeChallenge(state)).thenReturn(challenge);
        when(webAuthnLoginService.processCredentials("user@example.com", challenge, assertRequest, authenticators)).thenReturn(null);

        ResponseEntity<?> response = controller.loginFinish(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(true, body.get("verified"));
        assertEquals("FIDO2_USB_KEY", body.get("method"));
        verify(webAuthnCeremonyService).delete("tx-login-2");
    }

    @Test
    void loginFinish_returnsUnauthorizedWhenAssertionFails() {
        WebAuthnCeremonyState state = new WebAuthnCeremonyState();
        state.setUsername("user@example.com");

        WebAuthnLoginFinishRequest request = new WebAuthnLoginFinishRequest();
        request.setTransactionId("tx-login-3");
        AssertRequest assertRequest = new AssertRequest();
        request.setAssertion(assertRequest);

        Set<CredentialRecord> authenticators = Set.of(mock(CredentialRecord.class));
        byte[] challenge = new byte[]{1, 1, 1};

        when(webAuthnCeremonyService.findByType("tx-login-3", WebAuthnCeremonyState.CeremonyType.AUTHENTICATION))
                .thenReturn(Optional.of(state));
        when(userAuthenticatorRepository.load("user@example.com")).thenReturn(authenticators);
        when(webAuthnCeremonyService.decodeChallenge(state)).thenReturn(challenge);
        when(webAuthnLoginService.processCredentials("user@example.com", challenge, assertRequest, authenticators))
                .thenThrow(new IllegalStateException("invalid assertion"));

        ResponseEntity<?> response = controller.loginFinish(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("FIDO2 assertion verification failed", body.get("error"));
    }
}
