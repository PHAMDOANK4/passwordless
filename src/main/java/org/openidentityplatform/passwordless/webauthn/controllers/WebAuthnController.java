/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openidentityplatform.passwordless.webauthn.controllers;

import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.data.AuthenticatorAttachment;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/webauthn/v1")
@CrossOrigin(origins = {"http://localhost:1234", "http://localhost:8080", "https://authentication.k4.vn"},
        allowCredentials = "true"
        )
public class WebAuthnController {

    public static final String USERNAME_SESSION_ATTRIBUTE = "username";
    private final WebAuthnRegistrationService webAuthnRegistrationService;

    private final WebAuthnLoginService webAuthnLoginService;

    private final UserAuthenticatorRepository userAuthenticatorRepository;

    private final UserRepository userRepository;

    private final WebAuthnCeremonyService webAuthnCeremonyService;

    public WebAuthnController(WebAuthnRegistrationService webAuthnRegistrationService,
                              WebAuthnLoginService webAuthnLoginService,
                              UserAuthenticatorRepository userAuthenticatorRepository,
                              UserRepository userRepository,
                              WebAuthnCeremonyService webAuthnCeremonyService) {
        this.webAuthnRegistrationService = webAuthnRegistrationService;
        this.webAuthnLoginService = webAuthnLoginService;
        this.userAuthenticatorRepository = userAuthenticatorRepository;
        this.userRepository = userRepository;
        this.webAuthnCeremonyService = webAuthnCeremonyService;
    }


    @GetMapping("/register/challenge/{username}")
    public PublicKeyCredentialCreationOptions challenge(HttpServletRequest request,
                                                        @PathVariable("username") String username,
                                                        @RequestParam(value = "authenticatorAttachment", required = false) String authenticatorAttachment,
                                                        @RequestParam(value = "residentKeyRequired", required = false) Boolean residentKeyRequired,
                                                        @RequestParam(value = "userVerification", required = false) String userVerification) {

        String normalizedUsername = normalizeUsername(username);
        byte[] challengeBytes = request.getSession().getId().getBytes();

        PublicKeyCredentialCreationOptions credentialCreationOptions
                = webAuthnRegistrationService.requestCredentials(
                        normalizedUsername,
                        challengeBytes,
                        resolveAuthenticatorAttachment(authenticatorAttachment),
                        residentKeyRequired,
                        resolveUserVerification(userVerification)
                );

        request.getSession().setAttribute(USERNAME_SESSION_ATTRIBUTE, normalizedUsername);  // authenticated user

        return credentialCreationOptions;
    }

    @PostMapping("/register/credential")
    public Map<String, Object> registerCredential(@RequestBody CredentialRequest credentialRequest, HttpServletRequest request) {
        log.info("credential request:  {}", credentialRequest);

        String username = (String)request.getSession().getAttribute(USERNAME_SESSION_ATTRIBUTE);

        CredentialRecord credentialRecord = webAuthnRegistrationService.processCredentials(credentialRequest, request);

        userAuthenticatorRepository.save(username, credentialRecord);

        return Collections.singletonMap("credentialId", Base64.getUrlEncoder().encodeToString(credentialRecord.getAttestedCredentialData().getCredentialId()));
    }

    @RequestMapping("/login/challenge/{username}")
    public PublicKeyCredentialRequestOptions credentialRequest(HttpServletRequest request,
                                                               @PathVariable("username") String username) {

        Set<CredentialRecord> authenticators = userAuthenticatorRepository.load(username);
        PublicKeyCredentialRequestOptions credentialRequestOptions
                = webAuthnLoginService.requestCredentials(username, request, authenticators);

        request.getSession().setAttribute(USERNAME_SESSION_ATTRIBUTE, username);  //authencticated user

        return  credentialRequestOptions;
    }

    @RequestMapping("/login/challenge/")
    public PublicKeyCredentialRequestOptions credentialAnonRequest(HttpServletRequest request) {

        PublicKeyCredentialRequestOptions credentialRequestOptions
                = webAuthnLoginService.requestCredentials("", request, Collections.emptySet());

        request.getSession().setAttribute(USERNAME_SESSION_ATTRIBUTE, "");  //authencticated user

        return  credentialRequestOptions;
    }

    @PostMapping("/login/credential")
    public Map<String, Object> assertCredential(@RequestBody AssertRequest assertRequest, HttpServletRequest request) {

        log.info("assert request: {}", assertRequest);

        String username = (String)request.getSession().getAttribute(USERNAME_SESSION_ATTRIBUTE);

        Set<CredentialRecord> authenticators = userAuthenticatorRepository.load(username);

        AuthenticatorData<?> authenticatorData = webAuthnLoginService.processCredentials(username, request, assertRequest, authenticators);

        return Collections.singletonMap("response", authenticatorData);
    }

    @PostMapping("/register/begin")
    public ResponseEntity<?> registerBegin(@RequestBody @Valid WebAuthnRegisterBeginRequest request) {
        String username = normalizeUsername(request.getUsername());

        if (!userRepository.existsByEmail(username)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found. Register user first."));
        }

        byte[] challenge = webAuthnCeremonyService.generateChallenge();
        String transactionId = webAuthnCeremonyService.create(
                username,
                WebAuthnCeremonyState.CeremonyType.REGISTRATION,
                challenge
        );

        PublicKeyCredentialCreationOptions options = webAuthnRegistrationService.requestCredentials(
                username,
                challenge,
                resolveAuthenticatorAttachment(request.getAuthenticatorAttachment()),
                request.getResidentKeyRequired(),
                resolveUserVerification(request.getUserVerification())
        );

        return ResponseEntity.ok(new WebAuthnRegisterBeginResponse(transactionId, options));
    }

    @PostMapping("/register/finish")
    public ResponseEntity<?> registerFinish(@RequestBody @Valid WebAuthnRegisterFinishRequest request) {
        var stateOpt = webAuthnCeremonyService.findByType(
                request.getTransactionId(),
                WebAuthnCeremonyState.CeremonyType.REGISTRATION
        );

        if (stateOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Registration transaction is invalid or expired"));
        }

        WebAuthnCeremonyState state = stateOpt.get();
        try {
            CredentialRecord credentialRecord = webAuthnRegistrationService.processCredentials(
                    request.getCredential(),
                    webAuthnCeremonyService.decodeChallenge(state)
            );
            userAuthenticatorRepository.save(state.getUsername(), credentialRecord);
            webAuthnCeremonyService.delete(request.getTransactionId());

            return ResponseEntity.ok(Map.of(
                    "status", "registered",
                    "username", state.getUsername(),
                    "credentialId", Base64.getUrlEncoder().encodeToString(credentialRecord.getAttestedCredentialData().getCredentialId())
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "FIDO2 registration verification failed"));
        }
    }

    @PostMapping("/login/begin")
    public ResponseEntity<?> loginBegin(@RequestBody @Valid WebAuthnLoginBeginRequest request) {
        String username = normalizeUsername(request.getUsername());
        Set<CredentialRecord> authenticators = userAuthenticatorRepository.load(username);
        if (authenticators.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No FIDO2 credentials registered for this user"));
        }

        byte[] challenge = webAuthnCeremonyService.generateChallenge();
        String transactionId = webAuthnCeremonyService.create(
                username,
                WebAuthnCeremonyState.CeremonyType.AUTHENTICATION,
                challenge
        );

        PublicKeyCredentialRequestOptions options = webAuthnLoginService.requestCredentials(username, challenge, authenticators);
        return ResponseEntity.ok(new WebAuthnLoginBeginResponse(transactionId, options));
    }

    @PostMapping("/login/finish")
    public ResponseEntity<?> loginFinish(@RequestBody @Valid WebAuthnLoginFinishRequest request) {
        var stateOpt = webAuthnCeremonyService.findByType(
                request.getTransactionId(),
                WebAuthnCeremonyState.CeremonyType.AUTHENTICATION
        );
        if (stateOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Login transaction is invalid or expired"));
        }

        WebAuthnCeremonyState state = stateOpt.get();
        Set<CredentialRecord> authenticators = userAuthenticatorRepository.load(state.getUsername());
        if (authenticators.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No FIDO2 credentials registered for this user"));
        }

        try {
            webAuthnLoginService.processCredentials(
                    state.getUsername(),
                    webAuthnCeremonyService.decodeChallenge(state),
                    request.getAssertion(),
                    authenticators
            );

            webAuthnCeremonyService.delete(request.getTransactionId());
            return ResponseEntity.ok(Map.of(
                    "verified", true,
                    "username", state.getUsername(),
                    "method", "FIDO2_USB_KEY"
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "FIDO2 assertion verification failed"));
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private AuthenticatorAttachment resolveAuthenticatorAttachment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("cross-platform") || normalized.equals("cross_platform")
                || normalized.equals("usb") || normalized.equals("security-key")) {
            return AuthenticatorAttachment.CROSS_PLATFORM;
        }
        if (normalized.equals("platform")) {
            return AuthenticatorAttachment.PLATFORM;
        }
        return null;
    }

    private UserVerificationRequirement resolveUserVerification(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("required".equals(normalized)) {
            return UserVerificationRequirement.REQUIRED;
        }
        if ("discouraged".equals(normalized)) {
            return UserVerificationRequirement.DISCOURAGED;
        }
        return UserVerificationRequirement.PREFERRED;
    }

}
