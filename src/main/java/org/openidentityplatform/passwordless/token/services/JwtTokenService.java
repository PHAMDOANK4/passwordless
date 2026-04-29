package org.openidentityplatform.passwordless.token.services;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.token.configuration.TokenProperties;
import org.openidentityplatform.passwordless.token.models.SigningKey;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT token service using persisted RSA keys managed by {@link KeyManagementService}.
 * Supports multiple keys via {@code kid} in the JWT header for seamless key rotation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenService {

    private final TokenProperties tokenProperties;
    private final KeyManagementService keyManagementService;

    public String issueAccessToken(User user, String clientId) {
        return issueAccessToken(user, clientId, null);
    }

    public String issueAccessToken(User user, String clientId, String sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(tokenProperties.getAccessTokenLifetimeSeconds());
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(tokenProperties.getIssuer())
                .subject(user.getId())
                .audience(tokenProperties.getAudience())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("client_id", clientId)
                .claim("scope", "openid profile email")
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry));

        if (sessionId != null && !sessionId.isBlank()) {
            builder.claim("sid", sessionId);
        }

        return sign(builder.build());
    }

    public String issueClientCredentialsAccessToken(String clientId, String scope) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(tokenProperties.getAccessTokenLifetimeSeconds());
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(tokenProperties.getIssuer())
                .subject(clientId)
                .audience(tokenProperties.getAudience())
                .claim("client_id", clientId)
                .claim("scope", scope)
                .claim("grant_type", "client_credentials")
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .build();

        return sign(claimsSet);
    }

    public String issueIdToken(User user, String clientId, String nonce) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(tokenProperties.getAccessTokenLifetimeSeconds());

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(tokenProperties.getIssuer())
                .subject(user.getId())
                .audience(clientId)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("name", user.getDisplayName())
                .claim("preferred_username", user.getEmail())
                .claim("auth_time", now.getEpochSecond())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry));

        if (nonce != null && !nonce.isBlank()) {
            builder.claim("nonce", nonce);
        }

        return sign(builder.build());
    }

    public long getAccessTokenLifetimeSeconds() {
        return tokenProperties.getAccessTokenLifetimeSeconds();
    }

    /**
     * Validates an access token. Extracts {@code kid} from the JWT header
     * and looks up the corresponding public key from {@link KeyManagementService}.
     */
    public JWTClaimsSet validateAccessToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            // Resolve public key by kid from the JWT header
            String kid = jwt.getHeader().getKeyID();
            RSAPublicKey publicKey;
            if (kid != null && !kid.isBlank()) {
                publicKey = keyManagementService.getPublicKeyByKid(kid);
            } else {
                // Fallback for tokens issued before key management (active key)
                SigningKey activeKey = keyManagementService.getActiveKey();
                publicKey = keyManagementService.parsePublicKey(activeKey.getPublicKeyPem());
            }

            boolean validSignature = jwt.verify(new RSASSAVerifier(publicKey));
            if (!validSignature) {
                throw new IllegalArgumentException("Invalid access token signature");
            }

            JWTClaimsSet claimsSet = jwt.getJWTClaimsSet();
            Instant now = Instant.now();
            if (claimsSet.getExpirationTime() == null || claimsSet.getExpirationTime().toInstant().isBefore(now)) {
                throw new IllegalArgumentException("Access token is expired");
            }
            if (!tokenProperties.getIssuer().equals(claimsSet.getIssuer())) {
                throw new IllegalArgumentException("Access token issuer mismatch");
            }
            if (claimsSet.getAudience() == null || !claimsSet.getAudience().contains(tokenProperties.getAudience())) {
                throw new IllegalArgumentException("Access token audience mismatch");
            }
            return claimsSet;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid access token", e);
        }
    }

    /**
     * Returns a JWKS (JSON Web Key Set) containing all signing keys
     * (ACTIVE + INACTIVE) so previously issued tokens remain verifiable.
     */
    public Map<String, Object> getJwks() {
        List<Map<String, Object>> keys = keyManagementService.getAllPublicKeys().stream()
                .map(keyManagementService::toJwk)
                .toList();
        return Map.of("keys", keys);
    }

    public String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ---------------------------------------------------------------
    // Internal
    // ---------------------------------------------------------------

    /**
     * Signs a JWT using the current ACTIVE signing key.
     * The {@code kid} is included in the JWT header for key lookup during verification.
     */
    private String sign(JWTClaimsSet claimsSet) {
        try {
            SigningKey activeKey = keyManagementService.getActiveKey();
            RSAPrivateKey privateKey = keyManagementService.parsePrivateKey(activeKey.getPrivateKeyPem());

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(activeKey.getKid())
                            .build(),
                    claimsSet
            );
            signedJWT.sign(new RSASSASigner(privateKey));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign JWT", e);
        }
    }
}
