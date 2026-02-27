package org.openidentityplatform.passwordless.otp;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.apps.models.RegisteredApp;
import org.openidentityplatform.passwordless.apps.services.AppRegistrationService;
import org.openidentityplatform.passwordless.otp.repositories.SentOtpRepository;
import org.openidentityplatform.passwordless.otp.services.CapturingOtpSender;
import org.openidentityplatform.passwordless.otp.services.OtpSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
@Import(IT_OtpTest.TestOtpSenderConfig.class)
public class IT_OtpTest {

    @TestConfiguration
    static class TestOtpSenderConfig {
        @Bean("dummyOTPSender")
        @Primary
        public OtpSender capturingOtpSender() {
            return new CapturingOtpSender();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    SentOtpRepository sentOtpRepository;

    @Autowired
    AppRegistrationService appRegistrationService;

    @Autowired
    OtpSender otpSender;

    private String apiKey;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/otp/v1";
        sentOtpRepository.deleteAll();
        if (otpSender instanceof CapturingOtpSender capturing) {
            capturing.clear();
        }

        String appName = "it-otp-test-" + UUID.randomUUID().toString().substring(0, 8);
        RegisteredApp app = appRegistrationService.registerApp(appName, "Integration test app", 100, 1000);
        apiKey = app.getApiKey();
    }

    static final String DESTINATION = "+7999999999";

    final static String SEND_REQUEST_BODY = """
            {
                "destination": "+7999999999",
                "sender": "sms"
            }
            """;

    @Test
    void otpSendValidate() throws Exception {
        ValidatableResponse response = given()
                .header("X-API-Key", apiKey)
                .contentType(ContentType.JSON).body(SEND_REQUEST_BODY)
                .when()
                .post("/send")
                .then().log().all()
                .assertThat().statusCode(200)
                .body("sessionId", not(emptyString()));

        JsonPath jsonPath = response.extract().body().jsonPath();
        String sessionId = jsonPath.getString("sessionId");

        // Get the raw OTP from the capturing sender (OTP is BCrypt-hashed in DB)
        String rawOtp = ((CapturingOtpSender) otpSender).getLastOtp(DESTINATION);

        String verifyBody = """
                {
                    "sessionId": "%s",
                    "otp": "%s"
                }
                """.formatted(sessionId, rawOtp);

        given()
                .header("X-API-Key", apiKey)
                .contentType(ContentType.JSON)
                .body(verifyBody)
                .when().log().all()
                .post("/verify")
                .then().log().all()
                .assertThat().statusCode(200)
                .body("valid", equalTo(true));
    }

    @Test
    void testFrequentSend() {
        given()
                .header("X-API-Key", apiKey)
                .contentType(ContentType.JSON).body(SEND_REQUEST_BODY)
                .when()
                .post("/send")
                .then().log().all()
                .assertThat().statusCode(200)
                .body("sessionId", not(emptyString()));

        //check frequent sending
        given()
                .header("X-API-Key", apiKey)
                .contentType(ContentType.JSON).body(SEND_REQUEST_BODY)
                .when()
                .post("/send")
                .then().log().all()
                .assertThat().statusCode(400)
                .body("error", not(emptyString()));
    }
}
