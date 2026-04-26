package org.openidentityplatform.passwordless.totp.controllers;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.totp.services.QrService;
import org.openidentityplatform.passwordless.totp.services.TotpService;
import org.openidentityplatform.passwordless.totp.services.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = TotpRestController.class, properties = "security.api-key-filter.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
class TotpRestControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TotpService totpService;
    @MockBean
    private QrService qrService;

    private static final String REGISTER_REQUEST_BODY = """
            {
                "username": "john"
            }
            """;
    private static final String VERIFY_REQUEST_BODY = """
            {
                "username": "john",
                "totp": 123456
            }
            """;

    private static final String TOTP_URI = "otpauth://totp/ACME:john@acme.com?secret=ONSWG4TFOQ======&issuer=ACME";

    @Test
    void register_returnsQrCodeAndUri() throws Exception {
        final String qr = IOUtils.resourceToString("/totp/qr-base64.txt", StandardCharsets.UTF_8);
        when(totpService.register(anyString())).thenReturn(URI.create(TOTP_URI));
        when(qrService.generateQr(anyString())).thenReturn(qr);

        mvc.perform(post("/totp/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uri").value(TOTP_URI))
                .andExpect(jsonPath("$.qr").value(qr));
    }

    @Test
    void register_returnsBadRequestForInvalidPayload() throws Exception {
        mvc.perform(post("/totp/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_REQUEST_BODY.replace("john", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returnsInternalServerErrorWhenGenerationFails() throws Exception {
        when(totpService.register(anyString())).thenThrow(new RuntimeException(new InvalidKeyException()));

        mvc.perform(post("/totp/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_REQUEST_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal server error occurred"));
    }

    @Test
    void verify_returnsValidResult() throws Exception {
        when(totpService.verify(anyString(), anyInt())).thenReturn(true);

        mvc.perform(post("/totp/v1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void verify_returnsNotFoundWhenUserIsMissing() throws Exception {
        when(totpService.verify(anyString(), anyInt())).thenThrow(new UserNotFoundException());

        mvc.perform(post("/totp/v1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_REQUEST_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

}