package org.openidentityplatform.passwordless.iam.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to create a new domain
 */
@Data
public class CreateDomainRequest {
    
    @NotBlank(message = "Domain name is required")
    @Pattern(regexp = "^[a-z0-9]+([-.]?[a-z0-9]+)*\\.[a-z]{2,}$", 
             message = "Invalid domain name format")
    private String domainName;
    
    @NotBlank(message = "Display name is required")
    private String displayName;
    
    private String description;
    
    @NotBlank(message = "Owner email is required")
    @Email(message = "Invalid email format")
    private String ownerEmail;
    
    private Boolean requireMfa = false;
    
    private Integer maxUsers;
    
    private String logoUrl;
}
