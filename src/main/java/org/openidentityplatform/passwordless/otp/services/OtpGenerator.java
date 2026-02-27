package org.openidentityplatform.passwordless.otp.services;

import org.openidentityplatform.passwordless.otp.configuration.OtpSettings;
import org.openidentityplatform.passwordless.otp.models.SentOtp;

import java.security.SecureRandom;
import java.util.UUID;

public class OtpGenerator {
    
    private static final SecureRandom secureRandom = new SecureRandom();

    public SentOtp generateSentOTP(OtpSettings otpSettings, String destination) {
        String otp = generateOtpCode(otpSettings.getOtpLength(), otpSettings.isUseLetters(), otpSettings.isUseDigits());
        SentOtp sentOTP = new SentOtp();
        sentOTP.setSessionId(UUID.randomUUID());
        sentOTP.setExpireTime(System.currentTimeMillis() + otpSettings.getTtlMinutes() * 60 * 1000);
        sentOTP.setOtp(otp);
        sentOTP.setDestination(destination);
        sentOTP.setLastSentAt(System.currentTimeMillis());
        return sentOTP;
    }
    
    /**
     * Generate a cryptographically secure OTP code
     * @param length Length of the OTP code
     * @param useLetters Include letters in the OTP
     * @param useDigits Include digits in the OTP
     * @return Generated OTP code
     */
    private String generateOtpCode(int length, boolean useLetters, boolean useDigits) {
        if (!useLetters && !useDigits) {
            throw new IllegalArgumentException("At least one of useLetters or useDigits must be true");
        }
        
        String characters = "";
        if (useDigits) {
            characters += "0123456789";
        }
        if (useLetters) {
            characters += "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        }
        
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(characters.length());
            otp.append(characters.charAt(randomIndex));
        }
        
        return otp.toString();
    }
}
