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

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.openidentityplatform.passwordless.exceptions.NotFoundException;
import org.openidentityplatform.passwordless.otp.models.SendOtpRequest;
import org.openidentityplatform.passwordless.otp.models.SendOtpResult;
import org.openidentityplatform.passwordless.otp.models.VerifyOtpRequest;
import org.openidentityplatform.passwordless.otp.models.VerifyOtpResult;
import org.openidentityplatform.passwordless.otp.services.FrequentSendingForbidden;
import org.openidentityplatform.passwordless.otp.services.OtpService;
import org.openidentityplatform.passwordless.otp.services.OtpVerifyAttemptsExceeded;
import org.openidentityplatform.passwordless.otp.services.SendOtpException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/otp/v1")
public class OtpRestController {

    private static final String EMAIL_OTP_SETTING_ID = "emailOtp";

    private final OtpService otpService;

    @PostMapping("/register")
    public SendOtpResult register(@RequestBody @Valid SendOtpRequest sendOTPRequest)
            throws NotFoundException, SendOtpException, FrequentSendingForbidden {
        String senderSettingId = resolveRegistrationSender(sendOTPRequest.getSender());
        return otpService.send(senderSettingId, sendOTPRequest.getDestination());
    }

    @PostMapping("/send")
    public SendOtpResult send(@RequestBody @Valid SendOtpRequest sendOTPRequest)
            throws NotFoundException, SendOtpException, FrequentSendingForbidden {
        return otpService.send(sendOTPRequest.getSender(), sendOTPRequest.getDestination());
    }

    @PostMapping("/verify")
    public VerifyOtpResult verify(@RequestBody @Valid VerifyOtpRequest verifyOTPRequest) throws NotFoundException, OtpVerifyAttemptsExceeded {
        // Support two verification modes:
        // 1. Destination + OTP (Google/Microsoft style) - preferred
        // 2. SessionId + OTP (legacy backward compatibility)
        
        if (verifyOTPRequest.destination != null && !verifyOTPRequest.destination.trim().isEmpty()) {
            // New method: verify by destination + OTP
            return otpService.verifyByDestination(verifyOTPRequest.destination, verifyOTPRequest.otp);
        } else if (verifyOTPRequest.sessionId != null && !verifyOTPRequest.sessionId.trim().isEmpty()) {
            // Legacy method: verify by sessionId + OTP
            return otpService.verify(verifyOTPRequest.sessionId, verifyOTPRequest.otp);
        } else {
            throw new IllegalArgumentException("Either 'destination' or 'sessionId' must be provided");
        }
    }

    private String resolveRegistrationSender(String sender) {
        if (sender == null) {
            return EMAIL_OTP_SETTING_ID;
        }

        String normalizedSender = sender.trim();
        if (normalizedSender.isEmpty() || normalizedSender.contains("@")) {
            return EMAIL_OTP_SETTING_ID;
        }

        if ("emailLink".equalsIgnoreCase(normalizedSender)) {
            return EMAIL_OTP_SETTING_ID;
        }

        return normalizedSender;
    }





}
