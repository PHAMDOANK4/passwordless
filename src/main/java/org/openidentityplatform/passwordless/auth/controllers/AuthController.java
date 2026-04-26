package org.openidentityplatform.passwordless.auth.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.auth.configuration.AuthProperties;
import org.openidentityplatform.passwordless.auth.configuration.AuthSessionCookie;
import org.openidentityplatform.passwordless.auth.models.AuthLoginRequest;
import org.openidentityplatform.passwordless.auth.models.AuthLoginResponse;
import org.openidentityplatform.passwordless.auth.models.AuthMfaPreferenceResponse;
import org.openidentityplatform.passwordless.auth.models.AuthMeResponse;
import org.openidentityplatform.passwordless.auth.models.AuthRegisterRequest;
import org.openidentityplatform.passwordless.auth.models.AuthRegisterResponse;
import org.openidentityplatform.passwordless.auth.models.AuthSessionResponse;
import org.openidentityplatform.passwordless.auth.models.AuthTotpActivationRequest;
import org.openidentityplatform.passwordless.auth.models.AuthTotpRegistrationResponse;
import org.openidentityplatform.passwordless.auth.models.AuthVerifyRequest;
import org.openidentityplatform.passwordless.auth.models.AuthVerifyResponse;
import org.openidentityplatform.passwordless.auth.services.AuthOrchestratorService;
import org.openidentityplatform.passwordless.auth.services.InvalidAuthTransactionException;
import org.openidentityplatform.passwordless.otp.services.FrequentSendingForbidden;
import org.openidentityplatform.passwordless.otp.services.OtpVerifyAttemptsExceeded;
import org.openidentityplatform.passwordless.otp.services.SendOtpException;
import org.openidentityplatform.passwordless.totp.services.UserNotFoundException;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthOrchestratorService authOrchestratorService;
    private final AuthProperties authProperties;

    @PostMapping("/register")
    public ResponseEntity<AuthRegisterResponse> register(@RequestBody @Valid AuthRegisterRequest request)
            throws InvalidAuthTransactionException {
        return ResponseEntity.status(HttpStatus.CREATED).body(authOrchestratorService.register(request));
    }

    @PostMapping("/login")
    public AuthLoginResponse login(@RequestBody @Valid AuthLoginRequest request, HttpServletRequest httpRequest)
            throws SendOtpException, FrequentSendingForbidden, InvalidAuthTransactionException {
        return authOrchestratorService.login(request, httpRequest);
    }

    @PostMapping("/mfa/verify")
        public AuthVerifyResponse verify(
            @RequestBody @Valid AuthVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
        )
            throws InvalidAuthTransactionException, OtpVerifyAttemptsExceeded, UserNotFoundException {
        AuthVerifyResponse response = authOrchestratorService.verify(request, httpRequest);

        ResponseCookie idpSessionCookie = ResponseCookie.from(AuthSessionCookie.NAME, response.getSessionId())
            .httpOnly(true)
            .secure(httpRequest.isSecure())
            .path("/")
            .sameSite("Lax")
            .maxAge(authProperties.getSessionTtlSeconds())
            .build();
        httpResponse.addHeader("Set-Cookie", idpSessionCookie.toString());

        return response;
    }

    @PostMapping("/mfa/totp/register")
    public AuthTotpRegistrationResponse registerTotp(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) throws InvalidAuthTransactionException {
        return authOrchestratorService.registerTotp(authorization);
    }

    @PostMapping("/mfa/totp/activate")
    public AuthMfaPreferenceResponse activateTotp(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody @Valid AuthTotpActivationRequest request
    ) throws InvalidAuthTransactionException {
        return authOrchestratorService.activateTotp(authorization, request.getTotp());
    }

    @PostMapping("/mfa/webauthn/activate")
    public AuthMfaPreferenceResponse activateWebAuthn(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) throws InvalidAuthTransactionException {
        return authOrchestratorService.activateWebAuthn(authorization);
    }

    @PostMapping("/mfa/email/activate")
    public AuthMfaPreferenceResponse activateEmailOtp(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) throws InvalidAuthTransactionException {
        return authOrchestratorService.activateEmailOtp(authorization);
    }

    @GetMapping("/me")
    public AuthMeResponse me(@RequestHeader(value = "Authorization", required = false) String authorization)
            throws InvalidAuthTransactionException {
        return authOrchestratorService.me(authorization);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    )
            throws InvalidAuthTransactionException {
        Map<String, String> response = authOrchestratorService.logout(authorization);

        ResponseCookie expiredCookie = ResponseCookie.from(AuthSessionCookie.NAME, "")
                .httpOnly(true)
                .secure(httpRequest.isSecure())
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        httpResponse.addHeader("Set-Cookie", expiredCookie.toString());

        return response;
    }

    @GetMapping("/sessions")
    public List<AuthSessionResponse> sessions(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) throws InvalidAuthTransactionException {
        return authOrchestratorService.sessions(authorization);
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public Map<String, Object> revokeSession(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("sessionId") String sessionId
    ) throws InvalidAuthTransactionException {
        return authOrchestratorService.revokeSession(authorization, sessionId);
    }

    @PostMapping("/sessions/revoke-all")
    public Map<String, Object> revokeAllSessions(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) throws InvalidAuthTransactionException {
        return authOrchestratorService.revokeAllSessions(authorization);
    }
}
