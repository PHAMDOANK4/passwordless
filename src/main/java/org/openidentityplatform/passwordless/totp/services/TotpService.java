package org.openidentityplatform.passwordless.totp.services;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base32;
import org.openidentityplatform.passwordless.totp.configuration.TotpConfiguration;
import org.openidentityplatform.passwordless.totp.models.RegisteredTotp;
import org.openidentityplatform.passwordless.totp.repository.RegisteredTotpRepository;
import org.openidentityplatform.passwordless.iam.models.Domain;
import org.openidentityplatform.passwordless.iam.models.User;
import org.openidentityplatform.passwordless.iam.repositories.DomainRepository;
import org.openidentityplatform.passwordless.iam.repositories.UserRepository;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Optional;

@Service
@Log4j2
@AllArgsConstructor
public class TotpService {

    private RegisteredTotpRepository totpRepository;
    private UserRepository userRepository;
    private DomainRepository domainRepository;

    private TimeBasedOneTimePasswordGenerator generator;

    private TotpConfiguration totpConfiguration;

    private static final String URI_TEMPLATE = "otpauth://totp/{0}:{1}@{2}?secret={3}&issuer={0}";

    public URI register(String username) {
        Optional<RegisteredTotp> registeredTotpOptional = totpRepository.findById(username);
        final RegisteredTotp registeredTotp;
        if(registeredTotpOptional.isPresent()) {
            registeredTotp = registeredTotpOptional.get();
        } else {
            registeredTotp = new RegisteredTotp();
            registeredTotp.setUsername(username);
        }
        final String secret = generateKey();
        registeredTotp.setSecret(secret);
        
        // --- JIT PROVISIONING TO SUPPORT IAM ADMIN DASHBOARD ---
        Domain defaultDomain = domainRepository.findByDomainName("default.com")
                .orElseGet(() -> {
                    Domain d = new Domain();
                    d.setDomainName("default.com");
                    d.setDisplayName("Default Domain");
                    d.setOwnerEmail("admin@default.com");
                    return domainRepository.save(d);
                });
                
        User iamUser = userRepository.findByEmail(username)
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail(username);
                    u.setDomain(defaultDomain);
                    u.setDisplayName(username);
                    u.setRole(User.UserRole.USER);
                    u.setStatus(User.UserStatus.ACTIVE);
                    return userRepository.save(u);
                });
        registeredTotp.setUser(iamUser);
        // -------------------------------------------------------
        
        totpRepository.save(registeredTotp);
        final String issuerLabelEncoded = URLEncoder.encode(totpConfiguration.getIssuerLabel(), StandardCharsets.UTF_8);
        final String usernameEncoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        final String issuerEncoded = URLEncoder.encode(totpConfiguration.getIssuer(), StandardCharsets.UTF_8);

        final String uriStr = MessageFormat.format(URI_TEMPLATE, issuerLabelEncoded, usernameEncoded, issuerEncoded, secret);
        return URI.create(uriStr);
    }

    public String generateKey() {
        final Key key;
        try {
            final KeyGenerator keyGenerator = KeyGenerator.getInstance(generator.getAlgorithm());
            final int macLengthInBytes = Mac.getInstance(generator.getAlgorithm()).getMacLength();
            keyGenerator.init(macLengthInBytes * 8);

            key = keyGenerator.generateKey();
            return new Base32().encodeToString(key.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Key restoreKey(String keyStr) {
        byte[] b = new Base32().decode(keyStr);
        return new SecretKeySpec(b, 0, b.length, generator.getAlgorithm());
    }

    public boolean verify(String username, Integer totp) throws UserNotFoundException {
        Optional<RegisteredTotp> registeredTotpOptional = totpRepository.findById(username);
        if(registeredTotpOptional.isEmpty()) {
            throw new UserNotFoundException();
        }
        RegisteredTotp registeredTotp = registeredTotpOptional.get();
        Key key = restoreKey(registeredTotp.getSecret());

        final int generatedTotp;
        try {
            generatedTotp = generator.generateOneTimePassword(key, Instant.now());
        } catch (InvalidKeyException e) {
            log.error("totp generation error occurred", e);
            throw new RuntimeException(e);
        }
        return generatedTotp == totp;
    }
}
