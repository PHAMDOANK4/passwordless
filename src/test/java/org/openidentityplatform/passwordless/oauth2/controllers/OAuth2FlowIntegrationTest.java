package org.openidentityplatform.passwordless.oauth2.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.oauth2.models.AuthorizationCode;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
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
import org.springframework.test.web.servlet.MvcResult;

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
        String registerBody = """
                {
                  "email": "flow.user@acme.com",
                  "firstName": "Flow",
                  "lastName": "User"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("flow.user@acme.com"))
                .andExpect(jsonPath("$.domain").value("acme.com"));

        Domain domain = domainRepository.findByDomainName("acme.com").orElseThrow();
        User user = userRepository.findByEmail("flow.user@acme.com").orElseThrow();

        String clientId = "flow-client";
        String redirectUri = "https://client.example.com/callback";
        String codeVerifier = "plain-pkce-verifier-12345";

        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientSecret("unused-for-public-pkce-client");
        client.setClientName("Flow Integration Client");
        client.setDomain(domain);
        client.setRedirectUris(redirectUri);
        client.setAllowedScopes("openid profile email");
        client.setGrantTypes("authorization_code refresh_token");
        client.setActive(true);
        client.setRequirePkce(true);
        oAuthClientRepository.save(client);

        String userAccessToken = jwtTokenService.issueAccessToken(user, clientId);

        String authorizeBody = """
                {
                  "responseType": "code",
                  "clientId": "flow-client",
                  "redirectUri": "https://client.example.com/callback",
                  "scope": "openid profile email",
                  "state": "state-flow",
                  "codeChallenge": "plain-pkce-verifier-12345",
                  "codeChallengeMethod": "plain",
                  "nonce": "nonce-flow"
                }
                """;

        MvcResult authorizeResult = mockMvc.perform(post("/oauth2/authorize")
                        .header("Authorization", "Bearer " + userAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("state-flow"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn();

        JsonNode authorizeJson = objectMapper.readTree(authorizeResult.getResponse().getContentAsString());
        String authorizationCodeValue = authorizeJson.path("code").asText();
        assertFalse(authorizationCodeValue.isBlank());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", authorizationCodeValue)
                        .param("client_id", clientId)
                        .param("redirect_uri", redirectUri)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.scope").value("openid profile email"))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty());

        AuthorizationCode authorizationCode = authorizationCodeRepository.findByCode(authorizationCodeValue).orElseThrow();
        assertTrue(authorizationCode.isUsed());
    }
}
