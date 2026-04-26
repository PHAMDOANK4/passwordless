package org.openidentityplatform.passwordless.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.oauth2.models.AuthorizationCode;
import org.openidentityplatform.passwordless.oauth2.models.Session;
import org.openidentityplatform.passwordless.oauth2.models.Token;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.AuthorizationCodeRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.SessionRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.TokenRepository;
import org.openidentityplatform.passwordless.totp.models.RegisteredTotp;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorJPARepository;
import org.openidentityplatform.passwordless.webauthn.repositories.WebAuthnAuthenticatorEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private RegisteredTotpRepository registeredTotpRepository;

    @Autowired
    private UserAuthenticatorJPARepository userAuthenticatorJPARepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Autowired
    private OAuthClientRepository oAuthClientRepository;

    @BeforeEach
    void cleanData() {
      tokenRepository.deleteAll();
      authorizationCodeRepository.deleteAll();
        sessionRepository.deleteAll();
        registeredTotpRepository.deleteAll();
        userAuthenticatorJPARepository.deleteAll();
        userRepository.deleteAll();
        oAuthClientRepository.deleteAll();
        domainRepository.deleteAll();
    }

    @Test
    void createUser_createsUserAndDomain() throws Exception {
        String body = """
                {
                  "email": "new.user@acme.com",
                  "firstName": "New",
                  "lastName": "User",
                  "phoneNumber": "+12025550123",
                  "role": "USER",
                  "mfaEnabled": true,
                  "preferredMfaMethod": "OTP"
                }
                """;

        mockMvc.perform(post("/admin/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.user@acme.com"))
                .andExpect(jsonPath("$.displayName").value("New User"))
                .andExpect(jsonPath("$.mfaEnabled").value(true))
                .andExpect(jsonPath("$.preferredMfaMethod").value("EMAIL"));

        Domain domain = domainRepository.findByDomainName("acme.com").orElseThrow();
        User user = userRepository.findByEmail("new.user@acme.com").orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals("acme.com", domain.getDomainName());
        org.junit.jupiter.api.Assertions.assertEquals(domain.getId(), user.getDomain().getId());
    }

    @Test
    void getAuthRegistrations_returnsAggregatedStatus() throws Exception {
        User user = seedUser("john@acme.com");

        RegisteredTotp totp = new RegisteredTotp();
        totp.setUsername(user.getEmail());
        totp.setSecret("secret");
        totp.setUser(user);
        registeredTotpRepository.save(totp);

        WebAuthnAuthenticatorEntity passkey = new WebAuthnAuthenticatorEntity();
        passkey.setUsername(user.getEmail());
        passkey.setCredentialId("cred-1");
        passkey.setAuthenticator("{}");
        passkey.setDeviceName("MacBook Pro");
        passkey.setUser(user);
        userAuthenticatorJPARepository.save(passkey);

        Session session = new Session();
        session.setSessionId("sess-1");
        session.setUser(user);
        session.setIpAddress("127.0.0.1");
        session.setDeviceInfo("JUnit");
        session.setCreatedAt(Instant.now());
        session.setLastActivityAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        session.setRevoked(false);
        session.setAuthMethod(Session.AuthMethod.WEBAUTHN);
        session.setAuthLevel(2);
        sessionRepository.save(session);

        mockMvc.perform(get("/admin/api/users/{id}/auth-registrations", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.otp.registered").value(true))
                .andExpect(jsonPath("$.totp.registered").value(true))
                .andExpect(jsonPath("$.totp.count").value(1))
                .andExpect(jsonPath("$.totp.usernames", hasSize(1)))
                .andExpect(jsonPath("$.webauthn.registered").value(true))
                .andExpect(jsonPath("$.webauthn.count").value(1))
                .andExpect(jsonPath("$.activeSessionCount").value(1));
    }

    @Test
    void setPreferredMfa_rejectsTotpWhenNoTotpRegistered() throws Exception {
        User user = seedUser("alice@acme.com");

        String body = """
                {
                  "preferredMfaMethod": "TOTP",
                  "mfaEnabled": true
                }
                """;

        mockMvc.perform(put("/admin/api/users/{id}/preferred-mfa", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User does not have registered TOTP key"));
    }

    @Test
    void setPreferredMfa_updatesToTotpWhenRegistered() throws Exception {
        User user = seedUser("mfa-user@acme.com");

        RegisteredTotp totp = new RegisteredTotp();
        totp.setUsername(user.getEmail());
        totp.setSecret("secret");
        totp.setUser(user);
        registeredTotpRepository.save(totp);

        String body = """
                {
                  "preferredMfaMethod": "TOTP"
                }
                """;

        mockMvc.perform(put("/admin/api/users/{id}/preferred-mfa", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredMfaMethod").value("TOTP"))
                .andExpect(jsonPath("$.mfaEnabled").value(true));
    }

          @Test
          void deleteUser_removesDependentOAuthRecordsAndUser() throws Exception {
            User user = seedUser("delete.me@acme.com");

            Session session = new Session();
            session.setSessionId("sess-delete-1");
            session.setUser(user);
            session.setIpAddress("127.0.0.1");
            session.setDeviceInfo("JUnit");
            session.setCreatedAt(Instant.now());
            session.setLastActivityAt(Instant.now());
            session.setExpiresAt(Instant.now().plusSeconds(3600));
            session.setRevoked(false);
            sessionRepository.save(session);

            Token token = new Token();
            token.setUser(user);
            token.setTokenType(Token.TokenType.ACCESS);
            token.setTokenValue("token-delete-1");
            token.setClientId("client-delete");
            token.setSessionId("sess-delete-1");
            token.setScopes("openid profile email");
            token.setExpiresAt(Instant.now().plusSeconds(3600));
            tokenRepository.save(token);

            AuthorizationCode authorizationCode = new AuthorizationCode();
            authorizationCode.setCode("code-delete-1");
            authorizationCode.setUser(user);
            authorizationCode.setClientId("client-delete");
            authorizationCode.setRedirectUri("https://client.example.com/callback");
            authorizationCode.setExpiresAt(Instant.now().plusSeconds(600));
            authorizationCodeRepository.save(authorizationCode);

            mockMvc.perform(delete("/admin/api/users/{id}", user.getId()))
                .andExpect(status().isNoContent());

            org.junit.jupiter.api.Assertions.assertFalse(userRepository.existsById(user.getId()));
            org.junit.jupiter.api.Assertions.assertEquals(0, tokenRepository.count());
            org.junit.jupiter.api.Assertions.assertEquals(0, authorizationCodeRepository.count());
            org.junit.jupiter.api.Assertions.assertEquals(0, sessionRepository.count());
          }

    private User seedUser(String email) {
        String domainName = email.substring(email.indexOf('@') + 1);

        Domain domain = new Domain();
        domain.setDomainName(domainName);
        domain.setDisplayName(domainName);
        domain.setOwnerEmail("owner@" + domainName);
        domain.setActive(true);
        Domain savedDomain = domainRepository.save(domain);

        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setDisplayName("Test User");
        user.setDomain(savedDomain);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setRole(User.UserRole.USER);
        user.setMfaEnabled(false);

        return userRepository.save(user);
    }
}