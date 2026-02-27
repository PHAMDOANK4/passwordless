package org.openidentityplatform.passwordless.iam.dto;

import lombok.Data;
import java.time.Instant;

/**
 * Response containing user information
 */
@Data
public class UserResponse {
    private String id;
    private String email;
    private String domainId;
    private String domainName;
    private String firstName;
    private String lastName;
    private String displayName;
    private String phoneNumber;
    private String status;
    private boolean mfaEnabled;
    private String preferredMfaMethod;
    private String role;
    private String profilePictureUrl;
    private String locale;
    private String timezone;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
}
