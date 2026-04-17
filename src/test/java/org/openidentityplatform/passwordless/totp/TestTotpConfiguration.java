package org.openidentityplatform.passwordless.totp;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.totp.configuration.TotpConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class TestTotpConfiguration {

    @Autowired
    TotpConfiguration totpConfiguration;
    @Autowired
    TimeBasedOneTimePasswordGenerator totpGenerator;
    @Test
    public void testConfiguration() {
        assertEquals("test.com", totpConfiguration.getIssuer());
        assertNotNull(totpGenerator);
    }
}
