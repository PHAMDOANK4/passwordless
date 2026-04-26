package org.openidentityplatform.passwordless.auth.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthLoginRequest {

    @NotBlank
    @JsonAlias({"email", "username"})
    private String identifier;

    private String clientId = "passwordless-web";

    private AuthMethod preferredMethod;
}
