package org.openidentityplatform.passwordless.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private long accessTokenExpirationMinutes = 15;
    private long refreshTokenExpirationDays = 7;
    private String issuer = "passwordless-auth";
}
