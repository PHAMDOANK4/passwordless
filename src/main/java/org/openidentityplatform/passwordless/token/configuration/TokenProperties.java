package org.openidentityplatform.passwordless.token.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "token")
@Getter
@Setter
public class TokenProperties {

    private String issuer = "https://passwordless.actvn";
    private String audience = "passwordless-clients";

    private long accessTokenLifetimeSeconds = 900;
    private long refreshTokenLifetimeSeconds = 2592000;

    private String signingSecret = "change-me-token-signing-secret";
}
