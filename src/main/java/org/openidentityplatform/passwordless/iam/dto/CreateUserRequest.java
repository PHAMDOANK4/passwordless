package org.openidentityplatform.passwordless.iam.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to create a new user
 */
@Data
public class CreateUserRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "First name is required")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    private String lastName;
    
    private String phoneNumber;
    
    private String role = "USER";
    
    private Boolean mfaEnabled = false;
    
    private String preferredMfaMethod;
}
