package org.openidentityplatform.passwordless.totp;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.apps.models.RegisteredApp;
import org.openidentityplatform.passwordless.apps.services.AppRegistrationService;
import org.openidentityplatform.passwordless.totp.models.RegisteredTotp;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.totp.services.TotpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.Key;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
public class IT_Totp {
    @LocalServerPort
    private int port;

    @Autowired
    RegisteredTotpRepository registeredTotpRepository;

    @Autowired
    TotpService totpService;

    @Autowired
    TimeBasedOneTimePasswordGenerator totpGenerator;

    @Autowired
    AppRegistrationService appRegistrationService;

    private String apiKey;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/totp/v1";
        registeredTotpRepository.deleteAll();

        String appName = "it-totp-test-" + UUID.randomUUID().toString().substring(0, 8);
        RegisteredApp app = appRegistrationService.registerApp(appName, "Integration test app", 100, 1000);
        apiKey = app.getApiKey();
    }

    final static String USERNAME = "john";

    final static String REGISTER_REQUEST_BODY = """
            {
                "username": "%s"
            }
            """.formatted(USERNAME);

    final static String VERIFY_REQUEST_BODY = """
            {
                "username": "%s",
                "totp": "%s"
            }
            """;

    @Test
    void testRegister() {
        ValidatableResponse response = given()
                .header("X-API-Key", apiKey)
                .contentType(ContentType.JSON).body(REGISTER_REQUEST_BODY)
                .when()
                .post("/register")
                .then().log().all()
                .assertThat().statusCode(200)
                .body("uri", not(emptyString()))
                .body("image", not(emptyString()));

        JsonPath jsonPath = response.extract().body().jsonPath();
        String uriString = jsonPath.getString("uri");
        String qr = jsonPath.getString("qr");
        assertNotNull(qr);

        URI uri = URI.create(uriString);
        assertEquals("otpauth", uri.getScheme());

        Optional<RegisteredTotp> totp = registeredTotpRepository.findById(USERNAME);
        assertTrue(totp.isPresent());
        assertEquals(USERNAME, totp.get().getUsername());
        assertTrue(StringUtils.isNotEmpty(totp.get().getSecret()));
    }

    @Test
    void testVerify() throws InvalidKeyException {
        totpService.register(USERNAME);
        Optional<RegisteredTotp> registeredTotp = registeredTotpRepository.findById(USERNAME);
        assertTrue(registeredTotp.isPresent());
        Key key = totpService.restoreKey(registeredTotp.get().getSecret());
        int totp = totpGenerator.generateOneTimePassword(key, Instant.now());
        given()
                .header("X-API-Key", apiKey)
                .contentType(ContentType.JSON).body(VERIFY_REQUEST_BODY.formatted(USERNAME, totp))
                .when()
                .post("/verify")
                .then().log().all()
                .assertThat().statusCode(200)
                .body("valid", equalTo(true));
    }
}
