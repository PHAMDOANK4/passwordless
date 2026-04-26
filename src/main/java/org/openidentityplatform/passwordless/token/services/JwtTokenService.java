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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenService {

    private static final String KEY_ID = "local-dev-rs256";

    private final TokenProperties tokenProperties;

    private volatile KeyPair keyPair;

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

    public JWTClaimsSet validateAccessToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSAPublicKey publicKey = (RSAPublicKey) getKeyPair().getPublic();
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
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid access token", e);
        }
    }

    public Map<String, Object> getJwks() {
        RSAPublicKey publicKey = (RSAPublicKey) getKeyPair().getPublic();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(stripUnsigned(publicKey.getModulus().toByteArray()));
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(stripUnsigned(publicKey.getPublicExponent().toByteArray()));

        Map<String, Object> key = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", KEY_ID,
                "n", n,
                "e", e
        );

        return Map.of("keys", List.of(key));
    }

    private String sign(JWTClaimsSet claimsSet) {
        try {
            RSAPrivateKey privateKey = (RSAPrivateKey) getKeyPair().getPrivate();
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(KEY_ID)
                            .build(),
                    claimsSet
            );
            signedJWT.sign(new RSASSASigner(privateKey));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign JWT", e);
        }
    }

    private KeyPair getKeyPair() {
        KeyPair local = keyPair;
        if (local == null) {
            synchronized (this) {
                local = keyPair;
                if (local == null) {
                    keyPair = local = buildDeterministicKeyPair();
                }
            }
        }
        return local;
    }

    private KeyPair buildDeterministicKeyPair() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] seed = digest.digest(tokenProperties.getSigningSecret().getBytes(StandardCharsets.UTF_8));
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(seed);

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", KeyFactory.getInstance("RSA").getProvider());
            generator.initialize(2048, secureRandom);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to initialize RSA keys", e);
        } catch (Exception e) {
            log.error("Error generating deterministic RSA keypair", e);
            throw new IllegalStateException("Unable to initialize JWT signing keys", e);
        }
    }

    public String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] stripUnsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
