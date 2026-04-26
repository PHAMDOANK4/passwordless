package org.openidentityplatform.passwordless.auth.services;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openidentityplatform.passwordless.auth.configuration.AuthProperties;
import org.openidentityplatform.passwordless.auth.models.AuthLoginRequest;
import org.openidentityplatform.passwordless.auth.models.AuthLoginResponse;
import org.openidentityplatform.passwordless.auth.models.AuthMethod;
import org.openidentityplatform.passwordless.auth.models.AuthRegisterRequest;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.oauth2.services.SessionService;
import org.openidentityplatform.passwordless.otp.models.SendOtpResult;
import org.openidentityplatform.passwordless.otp.services.OtpService;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.openidentityplatform.passwordless.token.services.RefreshTokenService;
import org.openidentityplatform.passwordless.token.services.TokenBlacklistService;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.totp.services.QrService;
import org.openidentityplatform.passwordless.totp.services.TotpService;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorRepository;
import org.openidentityplatform.passwordless.webauthn.services.WebAuthnLoginService;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthOrchestratorServiceTest {

    @Mock
    private AuthTransactionService authTransactionService;
    @Mock
    private OtpService otpService;
    @Mock
    private TotpService totpService;
    @Mock
    private RegisteredTotpRepository registeredTotpRepository;
    @Mock
    private DomainRepository domainRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private UserAuthenticatorRepository userAuthenticatorRepository;
    @Mock
    private WebAuthnLoginService webAuthnLoginService;
    @Mock
    private SessionService sessionService;
    @Mock
    private AuthProperties authProperties;
    @Mock
    private QrService qrService;
    @Mock
    private HttpServletRequest httpServletRequest;

    private AuthOrchestratorService service;

    @BeforeEach
    void setUp() {
        service = new AuthOrchestratorService(
                authTransactionService,
                otpService,
                totpService,
                registeredTotpRepository,
                domainRepository,
                userRepository,
                refreshTokenService,
                jwtTokenService,
                tokenBlacklistService,
                userAuthenticatorRepository,
                webAuthnLoginService,
                sessionService,
                authProperties,
                qrService
        );
    }

    @Test
    void login_rejectsUnregisteredUser() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setIdentifier("missing@example.com");

        when(userRepository.findByEmailWithDomain("missing@example.com")).thenReturn(Optional.empty());

        InvalidAuthTransactionException ex = assertThrows(
                InvalidAuthTransactionException.class,
                () -> service.login(request, httpServletRequest)
        );

        assertEquals("User is not registered", ex.getMessage());
    }

    @Test
    void login_rejectsPreferredTotpWhenNotRegistered() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setIdentifier("john@example.com");
        request.setPreferredMethod(AuthMethod.TOTP);

        User user = activeUser("john@example.com");
        when(userRepository.findByEmailWithDomain("john@example.com")).thenReturn(Optional.of(user));
        when(userAuthenticatorRepository.load("john@example.com")).thenReturn(Collections.emptySet());
        when(registeredTotpRepository.findByUsername("john@example.com")).thenReturn(Optional.empty());

        InvalidAuthTransactionException ex = assertThrows(
                InvalidAuthTransactionException.class,
                () -> service.login(request, httpServletRequest)
        );

        assertEquals("TOTP is not registered for this user", ex.getMessage());
    }

    @Test
    void login_rejectsPreferredWebauthnWhenNoPasskey() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setIdentifier("john@example.com");
        request.setPreferredMethod(AuthMethod.WEBAUTHN);

        User user = activeUser("john@example.com");
        when(userRepository.findByEmailWithDomain("john@example.com")).thenReturn(Optional.of(user));
        when(userAuthenticatorRepository.load("john@example.com")).thenReturn(Collections.emptySet());
        when(registeredTotpRepository.findByUsername("john@example.com")).thenReturn(Optional.empty());

        InvalidAuthTransactionException ex = assertThrows(
                InvalidAuthTransactionException.class,
                () -> service.login(request, httpServletRequest)
        );

        assertEquals("WebAuthn passkey is not registered for this user", ex.getMessage());
    }

    @Test
    void login_rejectsLocalAuthWhenDomainRequiresSso() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setIdentifier("sso-user@acme.com");

        Domain domain = new Domain();
        domain.setDomainName("acme.com");
        domain.setSsoEnabled(true);

        User user = activeUser("sso-user@acme.com");
        user.setDomain(domain);

        when(userRepository.findByEmailWithDomain("sso-user@acme.com")).thenReturn(Optional.of(user));

        InvalidAuthTransactionException ex = assertThrows(
                InvalidAuthTransactionException.class,
                () -> service.login(request, httpServletRequest)
        );

        assertEquals("This domain requires SSO and does not allow local login", ex.getMessage());
    }

    @Test
    void login_withPreferredOtp_sendsEmailOtp() throws Exception {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setIdentifier("john@example.com");
        request.setPreferredMethod(AuthMethod.OTP);

        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");

        User user = activeUser("john@example.com");
        when(userRepository.findByEmailWithDomain("john@example.com")).thenReturn(Optional.of(user));
        when(userAuthenticatorRepository.load("john@example.com")).thenReturn(Collections.emptySet());
        when(registeredTotpRepository.findByUsername("john@example.com")).thenReturn(Optional.empty());

        var tx = new org.openidentityplatform.passwordless.auth.models.AuthTransactionState();
        tx.setId("tx-1");
        tx.setExpiresAt(Instant.now().plusSeconds(300));

        when(authTransactionService.create(
                eq("john@example.com"),
                eq("passwordless-web"),
                eq(AuthMethod.OTP),
                anyString(),
                any()
        )).thenReturn(tx);

        SendOtpResult sendOtpResult = new SendOtpResult(
                "sid-1",
                "john@example.com",
                Instant.now().plusSeconds(60).toEpochMilli(),
                5
        );
        when(otpService.send("emailOtp", "john@example.com")).thenReturn(sendOtpResult);

        AuthLoginResponse response = service.login(request, httpServletRequest);

        assertEquals(AuthMethod.OTP, response.getSelectedMethod());
        assertEquals("VERIFY", response.getNextStep());
        assertNotNull(response.getChallenge());
        verify(otpService).send("emailOtp", "john@example.com");
    }

    @Test
    void register_rejectsExistingUser() {
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setEmail("john@example.com");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        InvalidAuthTransactionException ex = assertThrows(
                InvalidAuthTransactionException.class,
                () -> service.register(request)
        );

        assertEquals("User is already registered", ex.getMessage());
    }

    @Test
    void register_createsUserAndDomainFromEmail() throws Exception {
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setEmail("new.user@acme.com");
        request.setFirstName("New");
        request.setLastName("User");
        request.setPreferredMethod(AuthMethod.OTP);

        Domain domain = new Domain();
        domain.setId("dom-1");
        domain.setDomainName("acme.com");

        when(userRepository.existsByEmail("new.user@acme.com")).thenReturn(false);
        when(domainRepository.findByDomainName("acme.com")).thenReturn(Optional.of(domain));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId("user-1");
            return saved;
        });

        var response = service.register(request);

        assertEquals("user-1", response.getUserId());
        assertEquals("new.user@acme.com", response.getEmail());
        assertEquals("New User", response.getDisplayName());
        assertEquals("acme.com", response.getDomain());
        assertEquals("EMAIL", response.getPreferredMfaMethod());
        assertTrue(response.isMfaEnabled());
    }

    @Test
    void register_rejectsLocalAuthWhenDomainRequiresSso() {
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setEmail("new.user@acme.com");
        request.setFirstName("New");
        request.setLastName("User");

        Domain domain = new Domain();
        domain.setId("dom-1");
        domain.setDomainName("acme.com");
        domain.setSsoEnabled(true);

        when(userRepository.existsByEmail("new.user@acme.com")).thenReturn(false);
        when(domainRepository.findByDomainName("acme.com")).thenReturn(Optional.of(domain));

        InvalidAuthTransactionException ex = assertThrows(
                InvalidAuthTransactionException.class,
                () -> service.register(request)
        );

        assertEquals("This domain requires SSO and does not allow local registration", ex.getMessage());
    }

    private User activeUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setLockedUntil(null);
        user.setMfaEnabled(true);
        return user;
    }
}
