package com.cvlievera.stockmanagement.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.OptionalLong;

/** RFC 6238 TOTP (SHA-1, 6 digits, 30s step), compatible with Google Authenticator / Authy / 1Password. */
@Component
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int WINDOW = 1;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public TotpService() {
        this(Clock.systemUTC());
    }

    TotpService(Clock clock) {
        this.clock = clock;
    }

    public String generateSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String otpauthUri(String issuer, String account, String secret) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * @param lastUsedStep step of the last accepted code (replay protection), or null
     * @return the matched time step, or empty if the code is invalid or was already used
     */
    public OptionalLong verify(String secret, String code, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = clock.millis() / 1000 / STEP_SECONDS;
        OptionalLong match = OptionalLong.empty();
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            boolean ok = constantTimeEquals(codeAt(secret, step), code);
            if (ok && (lastUsedStep == null || step > lastUsedStep)) {
                match = OptionalLong.of(step);
            }
        }
        return match;
    }

    public String codeAt(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP computation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : s.toUpperCase().toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Invalid base32 character");
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
