package org.openidentityplatform.passwordless.auth.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "auth")
@Getter
@Setter
public class AuthProperties {

    private long transactionTtlSeconds = 300;
    private int lockoutMaxAttempts = 5;
    private long lockoutDurationSeconds = 900;
    private long sessionTtlSeconds = 86400;
}
