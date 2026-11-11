package com.cvlievera.stockmanagement.security;

import com.cvlievera.stockmanagement.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class SecurityPrimitivesTest {

    // RFC 6238 appendix B secret ("12345678901234567890") in base32
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private static Clock at(long epochSeconds) {
        return Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    // ---- TOTP

    @Test
    void totpMatchesRfc6238TestVector() {
        TotpService totp = new TotpService(at(59));
        assertEquals("287082", totp.codeAt(RFC_SECRET, 59 / 30));
        assertTrue(totp.verify(RFC_SECRET, "287082", null).isPresent());
    }

    @Test
    void totpAcceptsOneStepOfClockDriftButNotMore() {
        TotpService totp = new TotpService(at(59 + 30));
        assertTrue(totp.verify(RFC_SECRET, "287082", null).isPresent());
        TotpService tooLate = new TotpService(at(59 + 90));
        assertTrue(tooLate.verify(RFC_SECRET, "287082", null).isEmpty());
    }

    @Test
    void totpRejectsReplayOfAnAlreadyUsedStep() {
        TotpService totp = new TotpService(at(59));
        OptionalLong step = totp.verify(RFC_SECRET, "287082", null);
        assertTrue(totp.verify(RFC_SECRET, "287082", step.getAsLong()).isEmpty());
    }

    @Test
    void totpRejectsMalformedCodes() {
        TotpService totp = new TotpService(at(59));
        assertTrue(totp.verify(RFC_SECRET, "28708", null).isEmpty());
        assertTrue(totp.verify(RFC_SECRET, "abcdef", null).isEmpty());
        assertTrue(totp.verify(RFC_SECRET, null, null).isEmpty());
    }

    @Test
    void totpSecretsRoundTripThroughBase32() {
        String secret = new TotpService().generateSecret();
        assertEquals(32, secret.length());
        assertEquals(secret, TotpService.base32Encode(TotpService.base32Decode(secret)));
    }

    // ---- Password policy

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void passwordPolicyAcceptsStrongPassword() {
        assertDoesNotThrow(() -> policy.validate("Correct-Horse-9-Battery", "alice"));
    }

    @Test
    void passwordPolicyRejectsShortSimpleAndUsernamePasswords() {
        assertThrows(BadRequestException.class, () -> policy.validate("Short1!", "alice"));
        assertThrows(BadRequestException.class, () -> policy.validate("alllowercaseletters", "alice"));
        assertThrows(BadRequestException.class, () -> policy.validate("Alice-Rocks-2024!", "alice"));
        assertThrows(BadRequestException.class, () -> policy.validate(null, "alice"));
    }

    // ---- Secret cipher

    private static String key() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }

    @Test
    void cipherRoundTripsAndUsesFreshIvEachTime() {
        SecretCipher cipher = new SecretCipher(key());
        cipher.init();
        String a = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String b = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertNotEquals(a, b);
        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(a));
    }

    @Test
    void cipherDetectsTampering() {
        SecretCipher cipher = new SecretCipher(key());
        cipher.init();
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("secret"));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void cipherRejectsWrongKeyLength() {
        SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[16]));
        assertThrows(IllegalStateException.class, cipher::init);
    }

    // ---- Rate limiter

    @Test
    void rateLimiterBlocksAfterMaxThenRecoversAfterWindow() {
        long[] now = {0};
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now[0]); }
        };
        RateLimitFilter filter = new RateLimitFilter(new ObjectMapper(), clock, 3, 60_000);
        assertTrue(filter.tryAcquire("1.1.1.1"));
        assertTrue(filter.tryAcquire("1.1.1.1"));
        assertTrue(filter.tryAcquire("1.1.1.1"));
        assertFalse(filter.tryAcquire("1.1.1.1"));
        assertTrue(filter.tryAcquire("2.2.2.2"), "other clients are unaffected");
        now[0] = 61_000;
        assertTrue(filter.tryAcquire("1.1.1.1"));
    }
}
