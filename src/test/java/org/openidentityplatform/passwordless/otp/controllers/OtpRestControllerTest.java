/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openidentityplatform.passwordless.otp.controllers;

import org.junit.jupiter.api.Test;
import org.openidentityplatform.passwordless.otp.models.SendOtpResult;
import org.openidentityplatform.passwordless.otp.models.VerifyOtpResult;
import org.openidentityplatform.passwordless.otp.services.FrequentSendingForbidden;
import org.openidentityplatform.passwordless.otp.services.OtpService;
import org.openidentityplatform.passwordless.otp.services.OtpVerifyAttemptsExceeded;
import org.openidentityplatform.passwordless.otp.services.SenderNotFoundException;
import org.openidentityplatform.passwordless.otp.services.SessionNotFoundException;
import org.openidentityplatform.passwordless.otp.services.TemplateNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = OtpRestController.class, properties = "security.api-key-filter.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
class OtpRestControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private OtpService otpService;

    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final String DESTINATION = "+7999999999";
    private static final Long RESEND_ALLOWED_AT = System.currentTimeMillis() + 5 * 1000 * 60;
    private static final Integer REMAINING_ATTEMPTS = 5;
    private static final String SEND_REQUEST_BODY = """
            {
                "destination": "+7999999999",
                "sender": "sms"
            }
            """;

    private static final String VERIFY_OTP_REQUEST_BODY = """
            {
                "sessionId": "%s",
                "otp": "123456"
            }
            """.formatted(SESSION_ID);

    @Test
    void send_returnsSessionDetails() throws Exception {
        when(otpService.send(anyString(), anyString())).thenReturn(
                new SendOtpResult(SESSION_ID, DESTINATION, RESEND_ALLOWED_AT, REMAINING_ATTEMPTS)
        );

        mvc.perform(post("/otp/v1/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEND_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.destination").value(DESTINATION))
                .andExpect(jsonPath("$.resendAllowedAt").value(RESEND_ALLOWED_AT))
                .andExpect(jsonPath("$.remainingAttempts").value(REMAINING_ATTEMPTS));
    }

    @Test
    void send_returnsNotFoundWhenSenderIsMissing() throws Exception {
        when(otpService.send(anyString(), anyString()))
                .thenThrow(new SenderNotFoundException());

        mvc.perform(post("/otp/v1/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEND_REQUEST_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void send_returnsNotFoundWhenTemplateIsMissing() throws Exception {
        when(otpService.send(anyString(), anyString()))
                .thenThrow(new TemplateNotFoundException());

        mvc.perform(post("/otp/v1/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEND_REQUEST_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void send_returnsBadRequestForInvalidPayload() throws Exception {

        String requestBody = """
                {
                    "destination": "+7999999999",
                    "type": "bad"
                }
                """;

        mvc.perform(post("/otp/v1/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
        void send_returnsBadRequestWhenRateLimited() throws Exception {
                when(otpService.send(anyString(), anyString()))
                .thenThrow(new FrequentSendingForbidden());

        mvc.perform(post("/otp/v1/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SEND_REQUEST_BODY))
                .andExpect(status().isBadRequest());
    }


    @Test
        void verify_returnsValidResult() throws Exception {
                when(otpService.verify(anyString(), anyString()))
                .thenReturn(new VerifyOtpResult(true, null));

        mvc.perform(post("/otp/v1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_OTP_REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)));
    }

    @Test
        void verify_returnsNotFoundWhenSessionIsMissing() throws Exception {
                when(otpService.verify(anyString(), anyString()))
                .thenThrow(new SessionNotFoundException());

        mvc.perform(post("/otp/v1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_OTP_REQUEST_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", notNullValue()));
    }

    @Test
        void verify_returnsBadRequestWhenAttemptsAreExceeded() throws Exception {
                when(otpService.verify(anyString(), anyString()))
                .thenThrow(new OtpVerifyAttemptsExceeded());

        mvc.perform(post("/otp/v1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VERIFY_OTP_REQUEST_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", notNullValue()));
    }
}