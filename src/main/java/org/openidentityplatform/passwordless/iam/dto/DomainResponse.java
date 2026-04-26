package org.openidentityplatform.passwordless.iam.dto;

import lombok.Data;
import java.time.Instant;

/**
 * Response containing domain information
 */
@Data
public class DomainResponse {
    private String id;
    private String domainName;
    private String displayName;
    private String description;
    private String ownerEmail;
    private boolean active;
    private boolean requireMfa;
    private boolean ssoEnabled;
    private String ssoConfig;
    private Integer maxUsers;
    private Integer currentUsers;
    private String customLoginUrl;
    private String logoUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
