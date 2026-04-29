package org.openidentityplatform.passwordless.token.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openidentityplatform.passwordless.token.models.SigningKey;
import org.openidentityplatform.passwordless.token.repositories.SigningKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages RSA signing keys persisted in the database.
 * <ul>
 *   <li>On startup, ensures at least one ACTIVE key exists.</li>
 *   <li>Provides the active key for JWT signing (cached in memory).</li>
 *   <li>Supports key rotation: new ACTIVE key is generated, old one becomes INACTIVE.</li>
 *   <li>JWKS endpoint serves all keys (ACTIVE + INACTIVE) so old tokens can still be verified.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyManagementService {

    private static final int KEY_SIZE = 2048;

    private final SigningKeyRepository signingKeyRepository;

    /** In-memory cache of the current active key to avoid DB lookups on every JWT sign. */
    private final AtomicReference<SigningKey> cachedActiveKey = new AtomicReference<>();

    @PostConstruct
    public void initialize() {
        signingKeyRepository.findFirstByStatusOrderByCreatedAtDesc(SigningKey.KeyStatus.ACTIVE)
                .ifPresentOrElse(
                        key -> {
                            cachedActiveKey.set(key);
                            log.info("Loaded active signing key: kid={}", key.getKid());
                        },
                        () -> {
                            SigningKey key = generateAndPersistKey();
                            cachedActiveKey.set(key);
                            log.info("Generated initial signing key: kid={}", key.getKid());
                        }
                );
    }

    /**
     * Returns the current ACTIVE signing key. Uses in-memory cache.
     */
    public SigningKey getActiveKey() {
        SigningKey cached = cachedActiveKey.get();
        if (cached != null) {
            return cached;
        }
        // Fallback: load from DB
        SigningKey key = signingKeyRepository.findFirstByStatusOrderByCreatedAtDesc(SigningKey.KeyStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active signing key found"));
        cachedActiveKey.set(key);
        return key;
    }

    /**
     * Returns the public key for a given kid. Used during token validation.
     */
    public RSAPublicKey getPublicKeyByKid(String kid) {
        // Fast path: check cached active key
        SigningKey cached = cachedActiveKey.get();
        if (cached != null && cached.getKid().equals(kid)) {
            return parsePublicKey(cached.getPublicKeyPem());
        }
        // Slow path: query DB (covers rotated keys)
        SigningKey key = signingKeyRepository.findByKid(kid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown signing key kid: " + kid));
        return parsePublicKey(key.getPublicKeyPem());
    }

    /**
     * Returns all keys (ACTIVE + INACTIVE) for the JWKS endpoint.
     * Inactive keys remain in JWKS so previously issued tokens can be verified.
     */
    public List<SigningKey> getAllPublicKeys() {
        return signingKeyRepository.findAll();
    }

    /**
     * Rotates the signing key:
     * <ol>
     *   <li>Generate a new RSA key pair.</li>
     *   <li>Mark all currently ACTIVE keys as INACTIVE.</li>
     *   <li>Save the new key as ACTIVE.</li>
     *   <li>Update the in-memory cache.</li>
     * </ol>
     */
    @Transactional
    public SigningKey rotateKey() {
        // Mark all current active keys as inactive
        List<SigningKey> activeKeys = signingKeyRepository.findByStatus(SigningKey.KeyStatus.ACTIVE);
        for (SigningKey key : activeKeys) {
            key.setStatus(SigningKey.KeyStatus.INACTIVE);
            key.setRotatedAt(Instant.now());
            signingKeyRepository.save(key);
        }

        // Generate and persist new active key
        SigningKey newKey = generateAndPersistKey();
        cachedActiveKey.set(newKey);
        log.info("Key rotation complete. New active kid={}, retired {} key(s)", newKey.getKid(), activeKeys.size());
        return newKey;
    }

    /**
     * Converts a {@link SigningKey} to a JWK (JSON Web Key) map for the JWKS endpoint.
     */
    public Map<String, Object> toJwk(SigningKey key) {
        RSAPublicKey publicKey = parsePublicKey(key.getPublicKeyPem());
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(stripLeadingZero(publicKey.getModulus().toByteArray()));
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(stripLeadingZero(publicKey.getPublicExponent().toByteArray()));

        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", key.getAlgorithm());
        jwk.put("kid", key.getKid());
        jwk.put("n", n);
        jwk.put("e", e);
        return jwk;
    }

    public RSAPrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse RSA private key", e);
        }
    }

    public RSAPublicKey parsePublicKey(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse RSA public key", e);
        }
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private SigningKey generateAndPersistKey() {
        KeyPair keyPair = generateRsaKeyPair();

        SigningKey signingKey = new SigningKey();
        signingKey.setKid(generateKid());
        signingKey.setPrivateKeyPem(toPem(keyPair.getPrivate().getEncoded(), "PRIVATE KEY"));
        signingKey.setPublicKeyPem(toPem(keyPair.getPublic().getEncoded(), "PUBLIC KEY"));
        signingKey.setStatus(SigningKey.KeyStatus.ACTIVE);
        signingKey.setAlgorithm("RS256");
        signingKey.setKeySize(KEY_SIZE);
        return signingKeyRepository.save(signingKey);
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    private String generateKid() {
        return "key-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String toPem(byte[] encoded, String type) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----";
    }

    private byte[] stripLeadingZero(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
