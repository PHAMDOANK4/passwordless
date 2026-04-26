package org.openidentityplatform.passwordless.oauth2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.auth.configuration.AuthSessionCookie;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.oauth2.models.AuthorizationCode;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.models.Session;
import org.openidentityplatform.passwordless.oauth2.repositories.AuthorizationCodeRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.SessionRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.TokenRepository;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class OAuth2FlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthClientRepository oAuthClientRepository;

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

        @MockBean
        private StringRedisTemplate redisTemplate;

        @MockBean
        private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

        private static final String REDIRECT_URI = "https://client.example.com/callback";
        private static final String ALLOWED_SCOPES = "openid profile email";

    @BeforeEach
    void cleanData() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenRepository.deleteAll();
        authorizationCodeRepository.deleteAll();
        sessionRepository.deleteAll();
        oAuthClientRepository.deleteAll();
        userRepository.deleteAll();
        domainRepository.deleteAll();
    }

    @Test
    void registerAuthorizeAndTokenExchange_returnsOAuthTokens() throws Exception {
        User user = registerUser("flow.user@acme.com", "Flow", "User");
        Domain domain = domainRepository.findByDomainName("acme.com").orElseThrow();

        String clientId = "flow-client";
        String redirectUri = REDIRECT_URI;
        String codeVerifier = "pkce-verifier-12345-abcdef";
        String codeChallenge = s256(codeVerifier);

        createPkceClient(domain, clientId, redirectUri, "Flow Integration Client");

        String userAccessToken = jwtTokenService.issueAccessToken(user, clientId);

        String authorizeBody = """
                {
                  "responseType": "code",
                  "clientId": "%s",
                  "redirectUri": "%s",
                  "scope": "%s",
                  "state": "state-flow",
                  "codeChallenge": "%s",
                  "codeChallengeMethod": "S256",
                  "nonce": "nonce-flow"
                }
                """.formatted(clientId, redirectUri, ALLOWED_SCOPES, codeChallenge);

        ResultActions authorizeActions = authorizeWithBearerToken(userAccessToken, authorizeBody)
                .andExpect(jsonPath("$.state").value("state-flow"));
        MvcResult authorizeResult = authorizeActions.andReturn();

        JsonNode authorizeJson = objectMapper.readTree(authorizeResult.getResponse().getContentAsString());
        String authorizationCodeValue = authorizeJson.path("code").asText();
        assertFalse(authorizationCodeValue.isBlank());

        exchangeAuthorizationCode(authorizationCodeValue, clientId, redirectUri, codeVerifier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.scope").value(ALLOWED_SCOPES))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty());

        AuthorizationCode authorizationCode = authorizationCodeRepository.findByCode(authorizationCodeValue).orElseThrow();
        assertTrue(authorizationCode.isUsed());
    }

    @Test
    void browserSessionAuthorizeAndTokenExchange_returnsOAuthTokens() throws Exception {
        User user = registerUser("browser.user@acme.com", "Browser", "User");
        Domain domain = domainRepository.findByDomainName("acme.com").orElseThrow();

        String clientId = "browser-client";
        String redirectUri = REDIRECT_URI;
        String codeVerifier = "pkce-browser-verifier-12345-abcdef";
        String codeChallenge = s256(codeVerifier);

        createPkceClient(domain, clientId, redirectUri, "Browser Integration Client");

        Session session = new Session();
        session.setSessionId("browser-session-123");
        session.setUser(user);
        session.setIpAddress("127.0.0.1");
        session.setCreatedAt(Instant.now());
        session.setLastActivityAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        session.setRevoked(false);
        session.setAuthMethod(Session.AuthMethod.OTP);
        sessionRepository.save(session);

        String authorizeBody = """
                {
                  "responseType": "code",
                  "clientId": "%s",
                  "redirectUri": "%s",
                  "scope": "%s",
                  "state": "state-browser",
                  "codeChallenge": "%s",
                  "codeChallengeMethod": "S256",
                  "nonce": "nonce-browser"
                }
                """.formatted(clientId, redirectUri, ALLOWED_SCOPES, codeChallenge);

        ResultActions authorizeActions = authorizeWithSessionCookie(session.getSessionId(), authorizeBody)
                .andExpect(jsonPath("$.state").value("state-browser"));
        MvcResult authorizeResult = authorizeActions.andReturn();

        JsonNode authorizeJson = objectMapper.readTree(authorizeResult.getResponse().getContentAsString());
        String authorizationCodeValue = authorizeJson.path("code").asText();
        assertFalse(authorizationCodeValue.isBlank());

        exchangeAuthorizationCode(authorizationCodeValue, clientId, redirectUri, codeVerifier)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.scope").value(ALLOWED_SCOPES))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty());

        AuthorizationCode authorizationCode = authorizationCodeRepository.findByCode(authorizationCodeValue).orElseThrow();
        assertTrue(authorizationCode.isUsed());
    }

        @Test
        void clientCredentialsGrant_returnsAccessToken() throws Exception {
                Domain domain = new Domain();
                domain.setDomainName("svc.local");
                domain.setDisplayName("Service Domain");
                domain.setOwnerEmail("admin@svc.local");
                domain.setActive(true);
                domain = domainRepository.save(domain);

                OAuthClient serviceClient = new OAuthClient();
                serviceClient.setClientId("svc-client");
                serviceClient.setClientSecret(new BCryptPasswordEncoder().encode("svc-secret"));
                serviceClient.setClientName("Service Client");
                serviceClient.setDomain(domain);
                serviceClient.setRedirectUris("http://localhost/internal/callback");
                serviceClient.setAllowedScopes("api.read api.write");
                serviceClient.setGrantTypes("client_credentials");
                serviceClient.setActive(true);
                serviceClient.setRequirePkce(false);
                oAuthClientRepository.save(serviceClient);

                mockMvc.perform(post("/oauth2/token")
                                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                .param("grant_type", "client_credentials")
                                                .param("client_id", "svc-client")
                                                .param("client_secret", "svc-secret")
                                                .param("scope", "api.read"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.token_type").value("Bearer"))
                                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                                .andExpect(jsonPath("$.id_token").doesNotExist())
                                .andExpect(jsonPath("$.scope").value("api.read"))
                                .andExpect(jsonPath("$.access_token").isNotEmpty());
        }

        private User registerUser(String email, String firstName, String lastName) throws Exception {
                String registerBody = """
                                {
                                  "email": "%s",
                                  "firstName": "%s",
                                  "lastName": "%s"
                                }
                                """.formatted(email, firstName, lastName);

                mockMvc.perform(post("/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(registerBody))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.email").value(email))
                                .andExpect(jsonPath("$.domain").value("acme.com"));

                return userRepository.findByEmail(email).orElseThrow();
        }

        private OAuthClient createPkceClient(Domain domain, String clientId, String redirectUri, String clientName) {
                OAuthClient client = new OAuthClient();
                client.setClientId(clientId);
                client.setClientSecret("unused-for-public-pkce-client");
                client.setClientName(clientName);
                client.setDomain(domain);
                client.setRedirectUris(redirectUri);
                client.setAllowedScopes(ALLOWED_SCOPES);
                client.setGrantTypes("authorization_code refresh_token");
                client.setActive(true);
                client.setRequirePkce(true);
                return oAuthClientRepository.save(client);
        }

        private ResultActions authorizeWithBearerToken(String accessToken, String body) throws Exception {
                return mockMvc.perform(post("/oauth2/authorize")
                                                .header("Authorization", "Bearer " + accessToken)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").isNotEmpty());
        }

        private ResultActions authorizeWithSessionCookie(String sessionId, String body) throws Exception {
                return mockMvc.perform(post("/oauth2/authorize")
                                                .cookie(new Cookie(AuthSessionCookie.NAME, sessionId))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").isNotEmpty());
        }

        private ResultActions exchangeAuthorizationCode(String code, String clientId, String redirectUri, String codeVerifier) throws Exception {
                return mockMvc.perform(post("/oauth2/token")
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .param("grant_type", "authorization_code")
                                .param("code", code)
                                .param("client_id", clientId)
                                .param("redirect_uri", redirectUri)
                                .param("code_verifier", codeVerifier));
        }

        private String s256(String verifier) throws Exception {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        }
}
