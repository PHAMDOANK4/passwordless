package org.openidentityplatform.passwordless.otp.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test OTP sender that captures sent OTPs for verification in integration tests.
 */
public class CapturingOtpSender implements OtpSender {

    private static final Pattern OTP_PATTERN = Pattern.compile("(?:Code|code|Confirmation code): (\\S+)");
    private final Map<String, String> sentOtps = new ConcurrentHashMap<>();

    @Override
    public void sendOTP(String destination, String messageBody, String messageTitle) {
        Matcher matcher = OTP_PATTERN.matcher(messageBody);
        if (matcher.find()) {
            sentOtps.put(destination, matcher.group(1));
        }
    }

    public String getLastOtp(String destination) {
        return sentOtps.get(destination);
    }

    public void clear() {
        sentOtps.clear();
    }
}
