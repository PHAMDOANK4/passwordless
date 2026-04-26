package org.openidentityplatform.passwordless.iam.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateDomainRequest {

    @Pattern(regexp = "^[a-z0-9]+([-.]?[a-z0-9]+)*\\.[a-z]{2,}$", message = "Invalid domain name format")
    private String domainName;

    private String displayName;

    private String description;

    @Email(message = "Invalid email format")
    private String ownerEmail;

    private Boolean active;

    private Boolean requireMfa;

    private Boolean ssoEnabled;

    private String ssoConfig;

    private Integer maxUsers;

    private String logoUrl;

    private String customLoginUrl;
}