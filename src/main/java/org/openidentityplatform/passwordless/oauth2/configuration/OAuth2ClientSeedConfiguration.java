package org.openidentityplatform.passwordless.oauth2.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.oauth2.models.OAuthClient;
import org.openidentityplatform.passwordless.oauth2.repositories.OAuthClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OAuth2ClientSeedConfiguration {

    private final DomainRepository domainRepository;
    private final OAuthClientRepository oAuthClientRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    @Bean
    CommandLineRunner oauth2DefaultClientsSeeder(
            @Value("${oauth2.seed.enabled:true}") boolean seedEnabled,
            @Value("${oauth2.seed.domain-name:default.com}") String seedDomain,
            @Value("${oauth2.seed.web-client-id:web-pkce-client}") String webClientId,
            @Value("${oauth2.seed.web-redirect-uri:http://localhost:3000/callback}") String webRedirectUri,
            @Value("${oauth2.seed.service-client-id:service-client}") String serviceClientId,
            @Value("${oauth2.seed.service-client-secret:}") String configuredServiceSecret
    ) {
        return args -> {
            if (!seedEnabled) {
                return;
            }

            Domain domain = domainRepository.findByDomainName(seedDomain)
                    .orElseGet(() -> {
                        Domain d = new Domain();
                        d.setDomainName(seedDomain);
                        d.setDisplayName("Default Domain");
                        d.setOwnerEmail("admin@" + seedDomain);
                        d.setActive(true);
                        return domainRepository.save(d);
                    });

            seedPublicPkceClient(domain, webClientId, webRedirectUri);
            seedServiceClient(domain, serviceClientId, configuredServiceSecret);
        };
    }

    private void seedPublicPkceClient(Domain domain, String clientId, String redirectUri) {
        if (oAuthClientRepository.findByClientId(clientId).isPresent()) {
            return;
        }

        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientSecret(passwordEncoder.encode(generateSecret()));
        client.setClientName("Sample Web PKCE Client");
        client.setDomain(domain);
        client.setRedirectUris(redirectUri);
        client.setAllowedScopes("openid profile email");
        client.setGrantTypes("authorization_code,refresh_token");
        client.setActive(true);
        client.setRequirePkce(true);

        oAuthClientRepository.save(client);
        log.info("Seeded OAuth2 PKCE client: client_id={} redirect_uri={}", clientId, redirectUri);
    }

    private void seedServiceClient(Domain domain, String clientId, String configuredServiceSecret) {
        if (oAuthClientRepository.findByClientId(clientId).isPresent()) {
            return;
        }

        String serviceSecret = configuredServiceSecret == null || configuredServiceSecret.isBlank()
                ? generateSecret()
                : configuredServiceSecret;

        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientSecret(passwordEncoder.encode(serviceSecret));
        client.setClientName("Sample Service Client");
        client.setDomain(domain);
        client.setRedirectUris("http://localhost/internal/callback");
        client.setAllowedScopes("api.read api.write");
        client.setGrantTypes("client_credentials");
        client.setActive(true);
        client.setRequirePkce(false);

        oAuthClientRepository.save(client);

        if (configuredServiceSecret == null || configuredServiceSecret.isBlank()) {
            log.warn("Seeded OAuth2 service client: client_id={} generated_secret={} (set oauth2.seed.service-client-secret in production)",
                    clientId,
                    serviceSecret);
        } else {
            log.info("Seeded OAuth2 service client: client_id={}", clientId);
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "pcs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
