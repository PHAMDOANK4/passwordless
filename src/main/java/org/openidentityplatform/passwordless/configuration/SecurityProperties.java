package org.openidentityplatform.passwordless.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    private int maxFailedAttempts = 5;
    private int lockoutDurationMinutes = 15;
}
