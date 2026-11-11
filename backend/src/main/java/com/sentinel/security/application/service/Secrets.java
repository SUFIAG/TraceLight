package com.sentinel.security.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * The small cryptographic chores the security module repeats.
 *
 * <p>Centralised for two reasons: every secret in this module must be hashed
 * the same way or a token issued by one service will not verify in another,
 * and comparisons must be constant-time. A plain {@code equals} on a token
 * leaks its prefix through timing, which is enough to walk a short code.
 */
public final class Secrets {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] DIGITS = "0123456789".toCharArray();
    /** Base32 without padding, as authenticator apps expect. */
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Secrets() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time comparison. Never replace with {@code equals}. */
    public static boolean matches(String presented, String expected) {
        if (presented == null || expected == null) return false;
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    /** URL-safe random token, for links sent by email. */
    public static String token(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    /** Numeric code of the given length, for codes a person reads and types. */
    public static String numericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(DIGITS[RANDOM.nextInt(DIGITS.length)]);
        }
        return sb.toString();
    }

    /** Base32 seed for TOTP enrolment. 160 bits, the RFC 4226 recommendation. */
    public static String base32Secret() {
        byte[] buffer = new byte[20];
        RANDOM.nextBytes(buffer);
        StringBuilder sb = new StringBuilder();
        for (byte b : buffer) {
            sb.append(BASE32.charAt((b & 0xFF) % 32));
        }
        return sb.toString();
    }

    /** Decodes a Base32 seed. Input is uppercased and stripped of padding and spaces. */
    public static byte[] decodeBase32(String encoded) {
        String clean = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : clean.toCharArray()) {
            buffer = (buffer << 5) | BASE32.indexOf(c);
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return out;
    }

    /** Masks an email for display: {@code a***y@bank.com}. */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "—";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String masked = local.length() <= 2
                ? local.charAt(0) + "*"
                : local.charAt(0) + "*".repeat(Math.min(5, local.length() - 2)) + local.charAt(local.length() - 1);
        return masked + "@" + parts[1];
    }

    /** Masks a phone number for display: {@code •••• ••89}. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "—";
        return "••••••" + phone.substring(phone.length() - 2);
    }
}
