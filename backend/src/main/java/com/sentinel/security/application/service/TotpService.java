package com.sentinel.security.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Time-based one-time passwords, RFC 6238.
 *
 * <p>Implemented directly rather than pulled in as a dependency: it is forty
 * lines of HMAC, and a second factor is the last place to accept a transitive
 * supply-chain risk.
 *
 * <p>Verification accepts the step before and after the current one. Clocks
 * drift, and a user who types a correct code and is told it is wrong will turn
 * the feature off.
 */
@Service
@Slf4j
public class TotpService {

    private static final int DIGITS = 6;
    private static final int STEP_SECONDS = 30;
    /** Steps of drift tolerated on either side. One step is 30 seconds. */
    private static final int WINDOW = 1;

    /** The {@code otpauth://} URI an authenticator app scans. */
    public String provisioningUri(String secret, String email, String issuer) {
        return "otpauth://totp/"
                + URLEncoder.encode(issuer, StandardCharsets.UTF_8) + ":"
                + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** True when the code is valid for now, the previous step, or the next. */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null) return false;
        String clean = code.replaceAll("\\s", "");
        if (clean.length() != DIGITS || !clean.chars().allMatch(Character::isDigit)) return false;

        long counter = Instant.now().getEpochSecond() / STEP_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (Secrets.matches(clean, generate(secret, counter + offset))) {
                return true;
            }
        }
        return false;
    }

    /** The code for a given counter value. Exposed for enrolment self-checks. */
    public String generate(String secret, long counter) {
        try {
            byte[] key = Secrets.decodeBase32(secret);
            byte[] data = new byte[8];
            long value = counter;
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (value & 0xFF);
                value >>= 8;
            }

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            log.error("Could not compute a TOTP code: {}", e.getMessage());
            return "";
        }
    }
}
