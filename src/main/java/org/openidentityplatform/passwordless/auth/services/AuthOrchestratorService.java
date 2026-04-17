package org.openidentityplatform.passwordless.auth.services;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.auth.models.AuthLoginRequest;
import org.openidentityplatform.passwordless.auth.models.AuthLoginResponse;
import org.openidentityplatform.passwordless.auth.models.AuthMfaPreferenceResponse;
import org.openidentityplatform.passwordless.auth.models.AuthMeResponse;
import org.openidentityplatform.passwordless.auth.models.AuthMethod;
import org.openidentityplatform.passwordless.auth.models.AuthRegisterRequest;
import org.openidentityplatform.passwordless.auth.models.AuthRegisterResponse;
import org.openidentityplatform.passwordless.auth.models.AuthSessionResponse;
import org.openidentityplatform.passwordless.auth.models.AuthTotpRegistrationResponse;
import org.openidentityplatform.passwordless.auth.models.AuthTransactionState;
import org.openidentityplatform.passwordless.auth.models.AuthVerifyRequest;
import org.openidentityplatform.passwordless.auth.models.AuthVerifyResponse;
import org.openidentityplatform.passwordless.auth.configuration.AuthProperties;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.openidentityplatform.passwordless.oauth2.models.Session;
import org.openidentityplatform.passwordless.oauth2.services.SessionService;
import org.openidentityplatform.passwordless.otp.models.SendOtpResult;
import org.openidentityplatform.passwordless.otp.models.VerifyOtpResult;
import org.openidentityplatform.passwordless.otp.services.FrequentSendingForbidden;
import org.openidentityplatform.passwordless.otp.services.OtpService;
import org.openidentityplatform.passwordless.otp.services.OtpVerifyAttemptsExceeded;
import org.openidentityplatform.passwordless.otp.services.SendOtpException;
import org.openidentityplatform.passwordless.totp.services.QrService;
import org.openidentityplatform.passwordless.token.models.TokenPair;
import org.openidentityplatform.passwordless.token.services.JwtTokenService;
import org.openidentityplatform.passwordless.token.services.RefreshTokenService;
import org.openidentityplatform.passwordless.token.services.TokenBlacklistService;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.totp.services.TotpService;
import org.openidentityplatform.passwordless.totp.services.UserNotFoundException;
import org.openidentityplatform.passwordless.webauthn.repositories.UserAuthenticatorRepository;
import org.openidentityplatform.passwordless.webauthn.services.WebAuthnLoginService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@AllArgsConstructor
public class AuthOrchestratorService {

    private static final String EMAIL_OTP_SETTING_ID = "emailOtp";

    private final AuthTransactionService authTransactionService;
    private final OtpService otpService;
    private final TotpService totpService;
    private final RegisteredTotpRepository registeredTotpRepository;
    private final DomainRepository domainRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserAuthenticatorRepository userAuthenticatorRepository;
    private final WebAuthnLoginService webAuthnLoginService;
    private final SessionService sessionService;
    private final AuthProperties authProperties;
    private final QrService qrService;

    public AuthRegisterResponse register(AuthRegisterRequest request) throws InvalidAuthTransactionException {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new InvalidAuthTransactionException("User is already registered");
        }

        Domain domain = resolveDomainForEmail(email);

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setDisplayName((request.getFirstName().trim() + " " + request.getLastName().trim()).trim());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setDomain(domain);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setRole(User.UserRole.USER);

        if (request.getPreferredMethod() != null) {
            user.setPreferredMfaMethod(toUserMfaMethod(request.getPreferredMethod()));
            user.setMfaEnabled(true);
        } else {
            user.setMfaEnabled(Boolean.TRUE.equals(request.getMfaEnabled()));
        }

        User saved = userRepository.save(user);
        return new AuthRegisterResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getDisplayName(),
                saved.getDomain() != null ? saved.getDomain().getDomainName() : null,
                saved.getStatus().name(),
                saved.isMfaEnabled(),
                saved.getPreferredMfaMethod() != null ? saved.getPreferredMfaMethod().name() : null
        );
    }

    public AuthTotpRegistrationResponse registerTotp(String authorizationHeader)
            throws InvalidAuthTransactionException {
        User user = resolveCurrentUser(authorizationHeader);
        var uri = totpService.register(user.getEmail());
        String qr = qrService.generateQr(uri.toString());
        return new AuthTotpRegistrationResponse(user.getEmail(), uri.toString(), qr);
    }

    public AuthMfaPreferenceResponse activateTotp(String authorizationHeader, Integer totp)
            throws InvalidAuthTransactionException {
        if (totp == null) {
            throw new InvalidAuthTransactionException("TOTP is required");
        }

        User user = resolveCurrentUser(authorizationHeader);
        final boolean valid;
        try {
            valid = totpService.verify(user.getEmail(), totp);
        } catch (UserNotFoundException e) {
            throw new InvalidAuthTransactionException("TOTP key is not registered for this user");
        }

        if (!valid) {
            throw new InvalidAuthTransactionException("Invalid TOTP code");
        }

        user.setMfaEnabled(true);
        user.setPreferredMfaMethod(User.MfaMethod.TOTP);
        User saved = userRepository.save(user);
        return new AuthMfaPreferenceResponse(saved.isMfaEnabled(), saved.getPreferredMfaMethod().name());
    }

    public AuthMfaPreferenceResponse activateWebAuthn(String authorizationHeader)
            throws InvalidAuthTransactionException {
        User user = resolveCurrentUser(authorizationHeader);
        if (userAuthenticatorRepository.load(user.getEmail()).isEmpty()) {
            throw new InvalidAuthTransactionException("WebAuthn passkey is not registered for this user");
        }

        user.setMfaEnabled(true);
        user.setPreferredMfaMethod(User.MfaMethod.WEBAUTHN);
        User saved = userRepository.save(user);
        return new AuthMfaPreferenceResponse(saved.isMfaEnabled(), saved.getPreferredMfaMethod().name());
    }

    public AuthMfaPreferenceResponse activateEmailOtp(String authorizationHeader)
            throws InvalidAuthTransactionException {
        User user = resolveCurrentUser(authorizationHeader);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new InvalidAuthTransactionException("Email destination is not available for this user");
        }

        user.setMfaEnabled(true);
        user.setPreferredMfaMethod(User.MfaMethod.EMAIL);
        User saved = userRepository.save(user);
        return new AuthMfaPreferenceResponse(saved.isMfaEnabled(), saved.getPreferredMfaMethod().name());
    }

    public AuthLoginResponse login(AuthLoginRequest request, HttpServletRequest httpRequest)
            throws SendOtpException, FrequentSendingForbidden, InvalidAuthTransactionException {
        String identifier = request.getIdentifier().trim().toLowerCase();

        User user = userRepository.findByEmail(identifier)
                .orElseThrow(() -> new InvalidAuthTransactionException("User is not registered"));

        if (user.isLocked()) {
            throw new InvalidAuthTransactionException("Account is temporarily locked due to too many failed attempts");
        }
        if (!user.isActive()) {
            throw new InvalidAuthTransactionException("Account is not active");
        }

        AuthMethod method = selectMethod(user, request.getPreferredMethod());

        AuthTransactionState tx = authTransactionService.create(
                user.getEmail(),
                request.getClientId(),
                method,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );

        Object challenge = null;
        String message;
        if (method == AuthMethod.OTP) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                throw new InvalidAuthTransactionException("User does not have an email destination for OTP");
            }

            SendOtpResult sendOtpResult;
            try {
                sendOtpResult = otpService.send(EMAIL_OTP_SETTING_ID, user.getEmail());
            } catch (NotFoundException e) {
                throw new IllegalStateException("OTP sender configuration is invalid", e);
            }
            challenge = Map.of(
                    "destination", sendOtpResult.getDestination(),
                    "resendAllowedAt", sendOtpResult.getResendAllowedAt(),
                    "remainingAttempts", sendOtpResult.getRemainingAttempts()
            );
            message = "OTP sent to destination";
        } else if (method == AuthMethod.TOTP) {
            message = "Enter TOTP code from authenticator app";
        } else {
            Set<com.webauthn4j.credential.CredentialRecord> authenticators = userAuthenticatorRepository.load(user.getEmail());
            challenge = webAuthnLoginService.requestCredentials(user.getEmail(), httpRequest, authenticators);
            message = "Complete WebAuthn assertion";
        }

        return new AuthLoginResponse(
                tx.getId(),
                "VERIFY",
                method,
                Math.max(1, tx.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond()),
                message,
                challenge
        );
    }

    public AuthVerifyResponse verify(AuthVerifyRequest request, HttpServletRequest httpRequest)
            throws InvalidAuthTransactionException, OtpVerifyAttemptsExceeded, UserNotFoundException {
        AuthTransactionState tx = authTransactionService.find(request.getAuthTxId())
                .orElseThrow(() -> new InvalidAuthTransactionException("auth transaction not found"));

        if (tx.getExpiresAt().isBefore(Instant.now())) {
            authTransactionService.delete(tx.getId());
            throw new InvalidAuthTransactionException("auth transaction expired");
        }

        AuthMethod method = request.getMethod() != null ? request.getMethod() : tx.getMethod();
        boolean valid;

        if (method == AuthMethod.OTP) {
            if (request.getOtp() == null || request.getOtp().isBlank()) {
                throw new InvalidAuthTransactionException("OTP is required");
            }
            VerifyOtpResult result;
            try {
                result = otpService.verifyByDestination(tx.getIdentifier(), request.getOtp().trim());
            } catch (NotFoundException e) {
                onVerificationFailed(tx, tx.getIdentifier());
                throw new InvalidAuthTransactionException("OTP session not found or expired");
            }
            valid = result.isValid();
        } else if (method == AuthMethod.TOTP) {
            if (request.getTotp() == null) {
                throw new InvalidAuthTransactionException("TOTP is required");
            }
            valid = totpService.verify(tx.getIdentifier(), request.getTotp());
        } else {
            if (request.getWebauthnAssertion() == null) {
                throw new InvalidAuthTransactionException("WebAuthn assertion is required");
            }
            Set<com.webauthn4j.credential.CredentialRecord> authenticators = userAuthenticatorRepository.load(tx.getIdentifier());
            try {
                webAuthnLoginService.processCredentials(tx.getIdentifier(), httpRequest, request.getWebauthnAssertion(), authenticators);
                valid = true;
            } catch (RuntimeException e) {
                onVerificationFailed(tx, tx.getIdentifier());
                throw new InvalidAuthTransactionException("WebAuthn verification failed");
            }
        }

        if (!valid) {
            onVerificationFailed(tx, tx.getIdentifier());
            throw new InvalidAuthTransactionException("Authentication verification failed");
        }

        User user = userRepository.findByEmail(tx.getIdentifier())
                .orElseThrow(() -> new InvalidAuthTransactionException("User not found after verification"));

        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(httpRequest.getRemoteAddr());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        Session session = sessionService.createSession(
            user,
            httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent"),
            method
        );

        TokenPair pair = refreshTokenService.issueInitialTokenPair(
                user,
                tx.getClientId(),
                httpRequest.getRemoteAddr(),
            httpRequest.getHeader("User-Agent"),
            session.getSessionId()
        );

        authTransactionService.delete(tx.getId());

        return new AuthVerifyResponse(
                true,
                pair.getAccessToken(),
                pair.getRefreshToken(),
                "Bearer",
                pair.getAccessTokenExpiresIn(),
                session.getSessionId(),
                user.getId(),
                user.getEmail()
        );
    }

    public AuthMeResponse me(String authorizationHeader) throws InvalidAuthTransactionException {
        JWTClaimsSet claims = validateBearerClaims(authorizationHeader);

        String userId = claims.getSubject();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidAuthTransactionException("User not found"));

        return new AuthMeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus().name(),
                user.getRole().name(),
                user.isMfaEnabled(),
                user.getPreferredMfaMethod() != null ? user.getPreferredMfaMethod().name() : null
        );
    }

    public Map<String, String> logout(String authorizationHeader) throws InvalidAuthTransactionException {
        String token = extractBearerToken(authorizationHeader);
        JWTClaimsSet claims = jwtTokenService.validateAccessToken(token);

        if (claims.getJWTID() != null) {
            tokenBlacklistService.blacklist(
                    claims.getJWTID(),
                    claims.getSubject(),
                    claims.getExpirationTime().toInstant(),
                    "user_logout"
            );
        }

        Object sid = claims.getClaim("sid");
        if (sid instanceof String sessionId && !sessionId.isBlank()) {
            sessionService.revokeSession(sessionId, "user_logout");
        }

        return Map.of("status", "logged_out");
    }

    public List<AuthSessionResponse> sessions(String authorizationHeader) throws InvalidAuthTransactionException {
        JWTClaimsSet claims = validateBearerClaims(authorizationHeader);
        User user = userRepository.findById(claims.getSubject())
                .orElseThrow(() -> new InvalidAuthTransactionException("User not found"));

        String currentSessionId = null;
        Object sid = claims.getClaim("sid");
        if (sid instanceof String sidValue && !sidValue.isBlank()) {
            currentSessionId = sidValue;
        }

        String finalCurrentSessionId = currentSessionId;
        return sessionService.findActiveSessionsByUser(user).stream()
                .map(session -> new AuthSessionResponse(
                        session.getSessionId(),
                        session.getIpAddress(),
                        session.getDeviceInfo(),
                        session.getAuthMethod() != null ? session.getAuthMethod().name() : null,
                        session.getAuthLevel(),
                        session.getCreatedAt(),
                        session.getLastActivityAt(),
                        session.getExpiresAt(),
                        session.getSessionId().equals(finalCurrentSessionId)
                ))
                .toList();
    }

    public Map<String, Object> revokeSession(String authorizationHeader, String sessionId)
            throws InvalidAuthTransactionException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidAuthTransactionException("sessionId is required");
        }

        JWTClaimsSet claims = validateBearerClaims(authorizationHeader);
        User user = userRepository.findById(claims.getSubject())
                .orElseThrow(() -> new InvalidAuthTransactionException("User not found"));

        Session targetSession = sessionService.findActiveSession(sessionId)
                .orElseThrow(() -> new InvalidAuthTransactionException("Session not found or already inactive"));

        if (!targetSession.getUser().getId().equals(user.getId())) {
            throw new InvalidAuthTransactionException("Session does not belong to current user");
        }

        sessionService.revokeSession(sessionId, "user_revoke_session");
        return Map.of("status", "revoked", "sessionId", sessionId);
    }

    public Map<String, Object> revokeAllSessions(String authorizationHeader)
            throws InvalidAuthTransactionException {
        JWTClaimsSet claims = validateBearerClaims(authorizationHeader);
        User user = userRepository.findById(claims.getSubject())
                .orElseThrow(() -> new InvalidAuthTransactionException("User not found"));

        int revokedCount = sessionService.revokeAllUserSessions(user, "user_revoke_all_sessions");
        return Map.of("status", "revoked_all", "revokedCount", revokedCount);
    }

    private void onVerificationFailed(AuthTransactionState tx, String identifier) {
        tx.setAttempts(tx.getAttempts() + 1);
        authTransactionService.save(tx);

        userRepository.findByEmail(identifier).ifPresent(user -> {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);
            if (failedAttempts >= authProperties.getLockoutMaxAttempts()) {
                user.setLockedUntil(Instant.now().plusSeconds(authProperties.getLockoutDurationSeconds()));
                user.setFailedLoginAttempts(0);
            }
            userRepository.save(user);
        });
    }

    private AuthMethod selectMethod(User user, AuthMethod preferredMethod) throws InvalidAuthTransactionException {
        String username = user.getEmail();
        boolean hasPasskeys = !userAuthenticatorRepository.load(username).isEmpty();
        boolean hasTotp = registeredTotpRepository.findByUsername(username).isPresent();

        if (preferredMethod != null) {
            return switch (preferredMethod) {
                case OTP -> {
                    if (user.getEmail() == null || user.getEmail().isBlank()) {
                        throw new InvalidAuthTransactionException("OTP authentication requires a valid email");
                    }
                    yield AuthMethod.OTP;
                }
                case TOTP -> {
                    if (!hasTotp) {
                        throw new InvalidAuthTransactionException("TOTP is not registered for this user");
                    }
                    yield AuthMethod.TOTP;
                }
                case WEBAUTHN -> {
                    if (!hasPasskeys) {
                        throw new InvalidAuthTransactionException("WebAuthn passkey is not registered for this user");
                    }
                    yield AuthMethod.WEBAUTHN;
                }
            };
        }

        if (hasPasskeys) {
            return AuthMethod.WEBAUTHN;
        }

        if (hasTotp) {
            return AuthMethod.TOTP;
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return AuthMethod.OTP;
        }

        throw new InvalidAuthTransactionException("No authentication method is available for this user");
    }

    private String extractBearerToken(String authorizationHeader) throws InvalidAuthTransactionException {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidAuthTransactionException("Missing Bearer token");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private JWTClaimsSet validateBearerClaims(String authorizationHeader) throws InvalidAuthTransactionException {
        String token = extractBearerToken(authorizationHeader);
        JWTClaimsSet claims;
        try {
            claims = jwtTokenService.validateAccessToken(token);
        } catch (IllegalArgumentException e) {
            throw new InvalidAuthTransactionException("Invalid access token");
        }

        if (claims.getJWTID() != null && tokenBlacklistService.isBlacklisted(claims.getJWTID())) {
            throw new InvalidAuthTransactionException("Token is revoked");
        }

        Object sid = claims.getClaim("sid");
        if (sid instanceof String sessionId && !sessionId.isBlank() && !sessionService.isSessionActive(sessionId)) {
            throw new InvalidAuthTransactionException("Session is no longer active");
        }

        return claims;
    }

    private User resolveCurrentUser(String authorizationHeader) throws InvalidAuthTransactionException {
        JWTClaimsSet claims = validateBearerClaims(authorizationHeader);
        return userRepository.findById(claims.getSubject())
                .orElseThrow(() -> new InvalidAuthTransactionException("User not found"));
    }

    private Domain resolveDomainForEmail(String email) {
        String domainName = "default.com";
        int at = email.indexOf('@');
        if (at > -1 && at < email.length() - 1) {
            domainName = email.substring(at + 1).toLowerCase(Locale.ROOT);
        }

        String finalDomainName = domainName;
        return domainRepository.findByDomainName(domainName)
                .orElseGet(() -> {
                    Domain domain = new Domain();
                    domain.setDomainName(finalDomainName);
                    domain.setDisplayName(finalDomainName);
                    domain.setOwnerEmail(email);
                    domain.setActive(true);
                    return domainRepository.save(domain);
                });
    }

    private User.MfaMethod toUserMfaMethod(AuthMethod authMethod) {
        return switch (authMethod) {
            case OTP -> User.MfaMethod.EMAIL;
            case TOTP -> User.MfaMethod.TOTP;
            case WEBAUTHN -> User.MfaMethod.WEBAUTHN;
        };
    }
}
