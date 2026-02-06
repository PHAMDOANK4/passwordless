package org.openidentityplatform.passwordless.identity.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.identity.models.AppUser;
import org.openidentityplatform.passwordless.identity.models.GrantResultPayload;
import org.openidentityplatform.passwordless.identity.models.IssuedAccessGrant;
import org.openidentityplatform.passwordless.identity.models.RegisteredClient;
import org.openidentityplatform.passwordless.identity.repositories.AppUserRepository;
import org.openidentityplatform.passwordless.identity.repositories.IssuedAccessGrantRepository;
import org.openidentityplatform.passwordless.identity.repositories.RegisteredClientRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessGrantServiceTest {

    private static final String KNOWN_APP_KEY = "ecommerceA-key-2026";
    private static final String KNOWN_HANDLE = "phamdoan_user";
    private static final String AUTH_VIA_OTP = "otp";

    private AppUserRepository userRepo;
    private RegisteredClientRepository clientRepo;
    private IssuedAccessGrantRepository grantRepo;
    private AccessGrantService serviceUnderTest;

    private AppUser sampleUser;
    private RegisteredClient sampleClient;

    @BeforeEach
    void setUp() {
        userRepo = mock(AppUserRepository.class);
        clientRepo = mock(RegisteredClientRepository.class);
        grantRepo = mock(IssuedAccessGrantRepository.class);

        serviceUnderTest = new AccessGrantService(userRepo, clientRepo, grantRepo);

        sampleUser = new AppUser(KNOWN_HANDLE, "pham@example.vn", "+84900000000");
        sampleUser.setUserId(UUID.randomUUID());

        sampleClient = new RegisteredClient(
                KNOWN_APP_KEY, "s3cret!", "Ecommerce A", "https://ecommerce-a.vn/callback");
        sampleClient.setRcId(UUID.randomUUID());
    }

    @Test
    void produceGrant_happyPath_returnsBearerAndRefreshTokens() throws Exception {
        when(clientRepo.findByAppKey(eq(KNOWN_APP_KEY))).thenReturn(Optional.of(sampleClient));
        when(userRepo.findByHandle(eq(KNOWN_HANDLE))).thenReturn(Optional.of(sampleUser));

        GrantResultPayload outcome = serviceUnderTest.produceGrant(
                KNOWN_APP_KEY, KNOWN_HANDLE, AUTH_VIA_OTP);

        assertNotNull(outcome.getAccessToken(), "bearer token must be present");
        assertNotNull(outcome.getRefreshToken(), "refresh token must be present");
        assertTrue(outcome.getExpiresInSeconds() > 0, "expiry must be positive");
        assertEquals(AUTH_VIA_OTP, outcome.getAuthMethod());
        assertEquals(KNOWN_HANDLE, outcome.getUserHandle());

        verify(grantRepo, times(1)).save(any(IssuedAccessGrant.class));
    }

    @Test
    void produceGrant_unknownAppKey_throwsClientNotFound() {
        when(clientRepo.findByAppKey(eq("bogus-key"))).thenReturn(Optional.empty());

        assertThrows(ClientAppNotFoundException.class,
                () -> serviceUnderTest.produceGrant("bogus-key", KNOWN_HANDLE, AUTH_VIA_OTP));
    }

    @Test
    void produceGrant_inactiveClient_throwsClientNotFound() {
        sampleClient.setActive(false);
        when(clientRepo.findByAppKey(eq(KNOWN_APP_KEY))).thenReturn(Optional.of(sampleClient));

        assertThrows(ClientAppNotFoundException.class,
                () -> serviceUnderTest.produceGrant(KNOWN_APP_KEY, KNOWN_HANDLE, AUTH_VIA_OTP));
    }

    @Test
    void produceGrant_unknownUser_throwsUserNotFound() {
        when(clientRepo.findByAppKey(eq(KNOWN_APP_KEY))).thenReturn(Optional.of(sampleClient));
        when(userRepo.findByHandle(eq("ghost"))).thenReturn(Optional.empty());

        assertThrows(AppUserNotFoundException.class,
                () -> serviceUnderTest.produceGrant(KNOWN_APP_KEY, "ghost", AUTH_VIA_OTP));
    }

    @Test
    void produceGrant_disabledUser_throwsUserNotFound() {
        sampleUser.setActive(false);
        when(clientRepo.findByAppKey(eq(KNOWN_APP_KEY))).thenReturn(Optional.of(sampleClient));
        when(userRepo.findByHandle(eq(KNOWN_HANDLE))).thenReturn(Optional.of(sampleUser));

        assertThrows(AppUserNotFoundException.class,
                () -> serviceUnderTest.produceGrant(KNOWN_APP_KEY, KNOWN_HANDLE, AUTH_VIA_OTP));
    }

    @Test
    void validateBearerToken_validToken_returnsTrue() {
        IssuedAccessGrant activeGrant = new IssuedAccessGrant(
                sampleUser, sampleClient, "tok_abc", "ref_abc", AUTH_VIA_OTP,
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(grantRepo.findByBearerToken(eq("tok_abc"))).thenReturn(Optional.of(activeGrant));

        assertTrue(serviceUnderTest.validateBearerToken("tok_abc"));
    }

    @Test
    void validateBearerToken_expiredToken_returnsFalse() {
        IssuedAccessGrant staleGrant = new IssuedAccessGrant(
                sampleUser, sampleClient, "tok_old", "ref_old", AUTH_VIA_OTP,
                Instant.now().minus(5, ChronoUnit.MINUTES));
        when(grantRepo.findByBearerToken(eq("tok_old"))).thenReturn(Optional.of(staleGrant));

        assertFalse(serviceUnderTest.validateBearerToken("tok_old"));
    }

    @Test
    void validateBearerToken_unknownToken_returnsFalse() {
        when(grantRepo.findByBearerToken(eq("nonexistent"))).thenReturn(Optional.empty());

        assertFalse(serviceUnderTest.validateBearerToken("nonexistent"));
    }
}
