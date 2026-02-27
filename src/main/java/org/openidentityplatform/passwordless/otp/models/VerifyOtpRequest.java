package org.openidentityplatform.passwordless.otp.models;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerifyOtpRequest {
    // Optional: for backward compatibility with sessionId-based verification
    public String sessionId;

    @NotEmpty
    public String otp;

    // Optional: for destination-based verification (like Google/Microsoft)
    public String destination;
}
