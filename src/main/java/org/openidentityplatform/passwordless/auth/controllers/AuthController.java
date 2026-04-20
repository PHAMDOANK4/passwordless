package org.openidentityplatform.passwordless.auth.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openidentityplatform.passwordless.auth.dto.AuthResponse;
import org.openidentityplatform.passwordless.auth.dto.OtpLoginRequest;
import org.openidentityplatform.passwordless.auth.dto.RefreshTokenRequest;
import org.openidentityplatform.passwordless.auth.dto.TotpLoginRequest;
import org.openidentityplatform.passwordless.auth.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login/totp")
    public ResponseEntity<AuthResponse> loginWithTotp(@Valid @RequestBody TotpLoginRequest request,
                                                      HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authService.authenticateWithTotp(
                request.getEmail(), request.getTotpCode(), ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/otp")
    public ResponseEntity<AuthResponse> loginWithOtp(@Valid @RequestBody OtpLoginRequest request,
                                                     HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authService.authenticateWithOtp(
                request.getEmail(), request.getOtp(), ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
                                                     HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authService.refreshToken(
                request.getRefreshToken(), ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revokeToken(@Valid @RequestBody RefreshTokenRequest request) {
        authService.revokeRefreshToken(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
