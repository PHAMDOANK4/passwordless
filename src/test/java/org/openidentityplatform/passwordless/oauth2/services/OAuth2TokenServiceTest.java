package org.openidentityplatform.passwordless.oauth2.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openidentityplatform.passwordless.oauth2.dto.OAuth2TokenResponse;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.repositories.AuthorizationCodeRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.openidentityplatform.passwordless.token.services.RefreshTokenService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2TokenServiceTest {

    @Mock
    private AuthorizationCodeRepository authorizationCodeRepository;
    @Mock
    private OAuthClientRepository oAuthClientRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private SessionService sessionService;

    private OAuth2TokenService service;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        service = new OAuth2TokenService(
                authorizationCodeRepository,
                oAuthClientRepository,
                refreshTokenService,
                jwtTokenService,
                passwordEncoder,
                sessionService
        );
    }

    @Test
    void token_clientCredentialsGrant_returnsAccessToken() throws Exception {
        OAuthClient client = new OAuthClient();
        client.setClientId("svc-client");
        client.setClientSecret(passwordEncoder.encode("svc-secret"));
        client.setActive(true);
        client.setRequirePkce(false);
        client.setGrantTypes("client_credentials");
        client.setAllowedScopes("api.read api.write");

        when(oAuthClientRepository.findByClientId("svc-client")).thenReturn(Optional.of(client));
        when(jwtTokenService.issueClientCredentialsAccessToken("svc-client", "api.read")).thenReturn("jwt-1");
        when(jwtTokenService.getAccessTokenLifetimeSeconds()).thenReturn(900L);

        OAuth2TokenResponse response = service.token(Map.of(
                "grant_type", "client_credentials",
                "client_id", "svc-client",
                "client_secret", "svc-secret",
                "scope", "api.read"
        ), "127.0.0.1", "JUnit");

        assertEquals("jwt-1", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("api.read", response.getScope());
    }

    @Test
    void token_clientCredentialsGrant_rejectsScopeOutsideAllowList() {
        OAuthClient client = new OAuthClient();
        client.setClientId("svc-client");
        client.setClientSecret(passwordEncoder.encode("svc-secret"));
        client.setActive(true);
        client.setRequirePkce(false);
        client.setGrantTypes("client_credentials");
        client.setAllowedScopes("api.read");

        when(oAuthClientRepository.findByClientId("svc-client")).thenReturn(Optional.of(client));

        OAuth2FlowException exception = assertThrows(
                OAuth2FlowException.class,
                () -> service.token(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "svc-client",
                        "client_secret", "svc-secret",
                        "scope", "api.write"
                ), "127.0.0.1", "JUnit")
        );

        assertEquals("Invalid scope requested: api.write", exception.getMessage());
    }
}
