package com.cvlievera.stockmanagement.auth;

import com.cvlievera.stockmanagement.audit.AuditLogService;
import com.cvlievera.stockmanagement.exception.BadRequestException;
import com.cvlievera.stockmanagement.security.*;
import com.cvlievera.stockmanagement.user.Role;
import com.cvlievera.stockmanagement.user.User;
import com.cvlievera.stockmanagement.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    private static final String PASSWORD = "Correct-Horse-9-Battery";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository users;
    private RefreshTokenService refreshTokens;
    private RecoveryCodeRepository recoveryCodes;
    private final TotpService totp = new TotpService();
    private SecretCipher cipher;
    private JwtTokenProvider jwt;
    private AuthServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        refreshTokens = mock(RefreshTokenService.class);
        recoveryCodes = mock(RecoveryCodeRepository.class);
        cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
        cipher.init();
        jwt = new JwtTokenProvider("0123456789abcdef0123456789abcdef", 900_000);
        jwt.init();

        service = new AuthServiceImpl(users, encoder, new PasswordPolicy(), jwt, refreshTokens,
                recoveryCodes, totp, cipher, mock(AuditLogService.class));
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "lockoutMinutes", 15L);

        user = User.builder().id(1L).username("alice").passwordHash(encoder.encode(PASSWORD))
                .role(Role.STAFF).active(true).build();
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokens.startSession(any(), any())).thenReturn(new RefreshTokenService.Issued("raw-refresh", user));
    }

    private AuthDTO.LoginRequest creds(String pw) {
        return new AuthDTO.LoginRequest("alice", pw);
    }

    @Test
    void successfulLoginIssuesSessionWithoutExposingTokensInBody() {
        AuthDTO.AuthResult r = service.login(creds(PASSWORD), "1.2.3.4");
        assertTrue(r.hasSession());
        assertEquals("OK", r.body().getStatus());
        assertNull(r.body().getMfaToken());
        assertEquals("raw-refresh", r.refreshToken());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameError() {
        BadCredentialsException wrong = assertThrows(BadCredentialsException.class, () -> service.login(creds("nope-nope-nope"), "ip"));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        BadCredentialsException ghost = assertThrows(BadCredentialsException.class,
                () -> service.login(new AuthDTO.LoginRequest("ghost", "x"), "ip"));
        assertEquals(wrong.getMessage(), ghost.getMessage());
    }

    @Test
    void accountLocksAfterMaxFailuresAndRejectsEvenTheCorrectPassword() {
        for (int i = 0; i < 3; i++) {
            assertThrows(BadCredentialsException.class, () -> service.login(creds("wrong-password-1"), "ip"));
        }
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(LocalDateTime.now()));
        assertThrows(BadCredentialsException.class, () -> service.login(creds(PASSWORD), "ip"));
    }

    @Test
    void successResetsTheFailureCounter() {
        assertThrows(BadCredentialsException.class, () -> service.login(creds("wrong-password-1"), "ip"));
        assertEquals(1, user.getFailedAttempts());
        service.login(creds(PASSWORD), "ip");
        assertEquals(0, user.getFailedAttempts());
    }

    @Test
    void deactivatedUserCannotLogIn() {
        user.setActive(false);
        assertThrows(BadCredentialsException.class, () -> service.login(creds(PASSWORD), "ip"));
    }

    @Test
    void userWithTwoFactorGetsMfaChallengeAndNoSessionUntilCodeIsVerified() {
        String secret = totp.generateSecret();
        user.setTotpEnabled(true);
        user.setTotpSecretEnc(cipher.encrypt(secret));

        AuthDTO.AuthResult step1 = service.login(creds(PASSWORD), "ip");
        assertFalse(step1.hasSession());
        assertEquals("MFA_REQUIRED", step1.body().getStatus());

        String code = totp.codeAt(secret, System.currentTimeMillis() / 1000 / 30);
        AuthDTO.AuthResult step2 = service.verifyMfa(new AuthDTO.MfaVerifyRequest(step1.body().getMfaToken(), code), "ip");
        assertTrue(step2.hasSession());
    }

    @Test
    void wrongTotpCodeIsRejectedAndCountsTowardLockout() {
        user.setTotpEnabled(true);
        user.setTotpSecretEnc(cipher.encrypt(totp.generateSecret()));
        String mfa = service.login(creds(PASSWORD), "ip").body().getMfaToken();
        assertThrows(BadCredentialsException.class,
                () -> service.verifyMfa(new AuthDTO.MfaVerifyRequest(mfa, "000000"), "ip"));
        assertEquals(1, user.getFailedAttempts());
    }

    @Test
    void mfaTokenCannotBeUsedAsAnAccessToken() {
        assertTrue(jwt.parseAccess(jwt.generateMfaToken(user)).isEmpty());
        assertTrue(jwt.parseMfa(jwt.generateAccessToken(user, false)).isEmpty());
    }

    @Test
    void adminWithoutTwoFactorGetsRestrictedSession() {
        user.setRole(Role.ADMIN);
        AuthDTO.AuthResult r = service.login(creds(PASSWORD), "ip");
        assertTrue(r.body().getRequiredActions().contains("ENROLL_2FA"));
        assertTrue(jwt.parseAccess(r.accessToken()).orElseThrow().get("restricted", Boolean.class));
    }

    @Test
    void passwordChangeRequiresCurrentPasswordPolicyAndBumpsTokenVersion() {
        user.setMustChangePassword(true);
        assertThrows(BadRequestException.class, () -> service.changePassword(user,
                new AuthDTO.ChangePasswordRequest("wrong-current-pw", "Brand-New-Passw0rd!"), "ip"));
        assertThrows(BadRequestException.class, () -> service.changePassword(user,
                new AuthDTO.ChangePasswordRequest(PASSWORD, "weak"), "ip"));

        int before = user.getTokenVersion();
        AuthDTO.AuthResult r = service.changePassword(user,
                new AuthDTO.ChangePasswordRequest(PASSWORD, "Brand-New-Passw0rd!"), "ip");
        assertEquals(before + 1, user.getTokenVersion());
        assertFalse(user.isMustChangePassword());
        verify(refreshTokens).revokeAllForUser(1L);
        assertTrue(r.body().getRequiredActions().isEmpty());
    }

    @Test
    void enablingTwoFactorIssuesTenHashedRecoveryCodes() {
        String secret = totp.generateSecret();
        user.setTotpSecretEnc(cipher.encrypt(secret));
        String code = totp.codeAt(secret, System.currentTimeMillis() / 1000 / 30);

        AuthDTO.EnableResult r = service.enableTwoFactor(user, code, "ip");
        assertEquals(10, r.recoveryCodes().size());
        assertTrue(user.isTotpEnabled());
        verify(recoveryCodes, times(10)).save(any(RecoveryCode.class));
    }
}
