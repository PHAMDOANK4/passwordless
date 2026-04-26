package org.openidentityplatform.passwordless.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AdminDomainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private OAuthClientRepository oAuthClientRepository;

    @BeforeEach
    void cleanData() {
      oAuthClientRepository.deleteAll();
        domainRepository.deleteAll();
    }

    @Test
    void createDomain_exposesSsoEnabledInResponse() throws Exception {
        String body = """
                {
                  "domainName": "acme.com",
                  "displayName": "Acme",
                  "description": "Acme Corp",
                  "ownerEmail": "owner@acme.com",
                  "requireMfa": true,
                  "ssoEnabled": true,
                  "ssoConfig": "{\\\"issuer\\\":\\\"https://sso.acme.com\\\"}",
                  "maxUsers": 100,
                  "logoUrl": "https://acme.com/logo.png"
                }
                """;

        mockMvc.perform(post("/admin/api/domains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.domainName").value("acme.com"))
                .andExpect(jsonPath("$.requireMfa").value(true))
                .andExpect(jsonPath("$.ssoEnabled").value(true))
                .andExpect(jsonPath("$.ssoConfig").value("{\"issuer\":\"https://sso.acme.com\"}"));
    }

    @Test
    void updateDomain_canToggleSsoEnabled() throws Exception {
        Domain domain = new Domain();
        domain.setDomainName("acme.com");
        domain.setDisplayName("Acme");
        domain.setOwnerEmail("owner@acme.com");
        domain.setActive(true);
        domain.setRequireMfa(false);
        domain.setSsoEnabled(false);
        Domain saved = domainRepository.save(domain);

        String body = """
                {
                  "displayName": "Acme Updated",
                  "ssoEnabled": true,
                  "ssoConfig": "{\\\"issuer\\\":\\\"https://login.acme.com\\\"}",
                  "customLoginUrl": "https://login.acme.com"
                }
                """;

        mockMvc.perform(put("/admin/api/domains/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Acme Updated"))
                .andExpect(jsonPath("$.ssoEnabled").value(true))
                .andExpect(jsonPath("$.ssoConfig").value("{\"issuer\":\"https://login.acme.com\"}"))
                .andExpect(jsonPath("$.customLoginUrl").value("https://login.acme.com"));
    }

    @Test
    void listDomains_returnsSsoMetadata() throws Exception {
        Domain domain = new Domain();
        domain.setDomainName("acme.com");
        domain.setDisplayName("Acme");
        domain.setOwnerEmail("owner@acme.com");
        domain.setActive(true);
        domain.setRequireMfa(false);
        domain.setSsoEnabled(true);
        domainRepository.save(domain);

        mockMvc.perform(get("/admin/api/domains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ssoEnabled").value(true));
    }
}
