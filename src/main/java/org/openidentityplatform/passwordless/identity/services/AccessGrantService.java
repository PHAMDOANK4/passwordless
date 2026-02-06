package org.openidentityplatform.passwordless.identity.services;

import lombok.extern.log4j.Log4j2;
import org.openidentityplatform.passwordless.identity.models.AppUser;
import org.openidentityplatform.passwordless.identity.models.GrantResultPayload;
import org.openidentityplatform.passwordless.identity.models.IssuedAccessGrant;
import org.openidentityplatform.passwordless.identity.models.RegisteredClient;
import org.openidentityplatform.passwordless.identity.repositories.AppUserRepository;
import org.openidentityplatform.passwordless.identity.repositories.IssuedAccessGrantRepository;
import org.openidentityplatform.passwordless.identity.repositories.RegisteredClientRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Core service of AuthCentralA: after the user successfully passes
 * passwordless verification (OTP, TOTP, or WebAuthn), the calling
 * application invokes this service to obtain a bearer token that it
 * can subsequently use to access protected resources.
 *
 * Flow (matches the architecture diagram):
 *   1. Ecommerce A redirects user to AuthCentralA
 *   2. User authenticates via OTP / TOTP / WebAuthn
 *   3. Ecommerce A calls {@link #produceGrant} with the appKey
 *      and userHandle
 *   4. AuthCentralA validates both parties and returns a
 *      {@link GrantResultPayload} containing the bearer token
 */
@Service
@Log4j2
public class AccessGrantService {

    private static final int TOKEN_BYTE_LENGTH = 48;
    private static final long GRANT_LIFETIME_MINUTES = 60;

    private final AppUserRepository appUserRepo;
    private final RegisteredClientRepository clientRepo;
    private final IssuedAccessGrantRepository grantRepo;
    private final SecureRandom entropySource;

    public AccessGrantService(AppUserRepository appUserRepo,
                              RegisteredClientRepository clientRepo,
                              IssuedAccessGrantRepository grantRepo) {
        this.appUserRepo = appUserRepo;
        this.clientRepo = clientRepo;
        this.grantRepo = grantRepo;
        this.entropySource = new SecureRandom();
    }

    /**
     * Produces a new access grant (bearer + refresh tokens) after the
     * user has been authenticated via the specified method.
     *
     * @param appKey      identifies the calling application
     * @param userHandle  the unique handle of the authenticated user
     * @param authMethod  which passwordless method was used (otp / totp / webauthn)
     * @return payload that the calling application stores client-side
     * @throws ClientAppNotFoundException  if appKey does not match any registered client
     * @throws AppUserNotFoundException    if userHandle does not match any user
     */
    public GrantResultPayload produceGrant(String appKey,
                                           String userHandle,
                                           String authMethod)
            throws ClientAppNotFoundException, AppUserNotFoundException {

        Optional<RegisteredClient> clientOpt = clientRepo.findByAppKey(appKey);
        if (clientOpt.isEmpty() || !clientOpt.get().isActive()) {
            log.warn("grant request with unknown or inactive appKey={}", appKey);
            throw new ClientAppNotFoundException("no active client for appKey " + appKey);
        }

        Optional<AppUser> userOpt = appUserRepo.findByHandle(userHandle);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            log.warn("grant request for unknown or disabled user={}", userHandle);
            throw new AppUserNotFoundException("no active user for handle " + userHandle);
        }

        String bearerToken  = mintToken();
        String refreshToken = mintToken();
        Instant expiry = Instant.now().plus(GRANT_LIFETIME_MINUTES, ChronoUnit.MINUTES);

        IssuedAccessGrant grant = new IssuedAccessGrant(
                userOpt.get(),
                clientOpt.get(),
                bearerToken,
                refreshToken,
                authMethod,
                expiry
        );
        grantRepo.save(grant);

        log.info("grant issued for user={} client={} method={}",
                 userHandle, appKey, authMethod);

        return new GrantResultPayload(
                bearerToken,
                refreshToken,
                GRANT_LIFETIME_MINUTES * 60,
                authMethod,
                userHandle
        );
    }

    /**
     * Checks whether a bearer token is still valid (exists and not expired).
     */
    public boolean validateBearerToken(String bearerToken) {
        Optional<IssuedAccessGrant> grantOpt = grantRepo.findByBearerToken(bearerToken);
        if (grantOpt.isEmpty()) {
            return false;
        }
        return grantOpt.get().getValidUntil().isAfter(Instant.now());
    }

    /* ---- internal helpers ---- */

    private String mintToken() {
        byte[] buf = new byte[TOKEN_BYTE_LENGTH];
        entropySource.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
