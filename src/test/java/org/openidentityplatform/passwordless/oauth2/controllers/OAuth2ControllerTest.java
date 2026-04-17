package org.openidentityplatform.passwordless.oauth2.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2FlowException;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2AuthorizationService;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2TokenManagementService;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2TokenService;
import org.openidentityplatform.passwordless.oauth2.services.OAuth2UserInfoService;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = OAuth2Controller.class, properties = "security.api-key-filter.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
class OAuth2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuth2AuthorizationService authorizationService;

    @MockBean
    private OAuth2TokenService tokenService;

    @MockBean
    private OAuth2TokenManagementService tokenManagementService;

    @MockBean
    private OAuth2UserInfoService userInfoService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @Test
    void authorizeJson_returnsParsedCodeAndState_forCamelCaseRequest() throws Exception {
        when(authorizationService.authorize(
                any(),
                eq("code"),
                eq("web-app-prod"),
                eq("https://client.example.com/callback"),
                eq("openid profile email"),
                eq("state-123"),
                eq("challenge-123"),
                eq("S256"),
                eq("nonce-123"),
                any(HttpServletRequest.class)
        )).thenReturn("https://client.example.com/callback?code=abc123&state=state-123");

        String body = """
                {
                  "responseType": "code",
                  "clientId": "web-app-prod",
                  "redirectUri": "https://client.example.com/callback",
                  "scope": "openid profile email",
                  "state": "state-123",
                  "codeChallenge": "challenge-123",
                  "codeChallengeMethod": "S256",
                  "nonce": "nonce-123"
                }
                """;

        mockMvc.perform(post("/oauth2/authorize")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUri").value("https://client.example.com/callback?code=abc123&state=state-123"))
                .andExpect(jsonPath("$.code").value("abc123"))
                .andExpect(jsonPath("$.state").value("state-123"));
    }

    @Test
    void authorizeJson_acceptsSnakeCaseRequestFields() throws Exception {
        when(authorizationService.authorize(
                any(),
                eq("code"),
                eq("snake-client"),
                eq("https://client.example.com/cb"),
                eq("openid"),
                eq("s+1"),
                eq("pkce-cc"),
                eq("S256"),
                eq("nonce-1"),
                any(HttpServletRequest.class)
        )).thenReturn("https://client.example.com/cb?code=xyz789&state=s%2B1");

        String body = """
                {
                  "response_type": "code",
                  "client_id": "snake-client",
                  "redirect_uri": "https://client.example.com/cb",
                  "scope": "openid",
                  "state": "s+1",
                  "code_challenge": "pkce-cc",
                  "code_challenge_method": "S256",
                  "nonce": "nonce-1"
                }
                """;

        mockMvc.perform(post("/oauth2/authorize")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUri").value("https://client.example.com/cb?code=xyz789&state=s%2B1"))
                .andExpect(jsonPath("$.code").value("xyz789"))
                .andExpect(jsonPath("$.state").value("s+1"));
    }

    @Test
    void authorizeJson_rejectsMissingRequiredFields() throws Exception {
        String body = """
                {
                  "scope": "openid"
                }
                """;

        mockMvc.perform(post("/oauth2/authorize")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authorizationService);
    }

    @Test
    void authorizeJson_returnsBadRequest_forInvalidRedirectUri() throws Exception {
        when(authorizationService.authorize(
                any(),
                eq("code"),
                eq("web-app-prod"),
                eq("https://evil.example.com/callback"),
                eq("openid profile email"),
                eq("state-1"),
                eq("challenge-1"),
                eq("S256"),
                eq("nonce-1"),
                any(HttpServletRequest.class)
        )).thenThrow(new OAuth2FlowException("redirect_uri is not allowed for this client"));

        String body = """
                {
                  "responseType": "code",
                  "clientId": "web-app-prod",
                  "redirectUri": "https://evil.example.com/callback",
                  "scope": "openid profile email",
                  "state": "state-1",
                  "codeChallenge": "challenge-1",
                  "codeChallengeMethod": "S256",
                  "nonce": "nonce-1"
                }
                """;

        mockMvc.perform(post("/oauth2/authorize")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("redirect_uri is not allowed for this client"));
    }

    @Test
    void authorizeJson_returnsBadRequest_forUnsupportedResponseType() throws Exception {
        when(authorizationService.authorize(
                any(),
                eq("token"),
                eq("web-app-prod"),
                eq("https://client.example.com/callback"),
                eq("openid profile email"),
                eq("state-2"),
                eq("challenge-2"),
                eq("S256"),
                eq("nonce-2"),
                any(HttpServletRequest.class)
        )).thenThrow(new OAuth2FlowException("Unsupported response_type"));

        String body = """
                {
                  "responseType": "token",
                  "clientId": "web-app-prod",
                  "redirectUri": "https://client.example.com/callback",
                  "scope": "openid profile email",
                  "state": "state-2",
                  "codeChallenge": "challenge-2",
                  "codeChallengeMethod": "S256",
                  "nonce": "nonce-2"
                }
                """;

        mockMvc.perform(post("/oauth2/authorize")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported response_type"));
    }
}
