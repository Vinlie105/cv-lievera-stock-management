package com.cvlievera.stockmanagement.auth;

import com.cvlievera.stockmanagement.audit.AuditLogService;
import com.cvlievera.stockmanagement.exception.BadRequestException;
import com.cvlievera.stockmanagement.security.JwtTokenProvider;
import com.cvlievera.stockmanagement.security.PasswordPolicy;
import com.cvlievera.stockmanagement.security.SecretCipher;
import com.cvlievera.stockmanagement.security.TotpService;
import com.cvlievera.stockmanagement.user.Role;
import com.cvlievera.stockmanagement.user.User;
import com.cvlievera.stockmanagement.user.UserDTO;
import com.cvlievera.stockmanagement.user.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    static final String GENERIC_FAILURE = "Invalid credentials, or the account is temporarily locked";
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final String ISSUER = "CV Lievera Stock";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final TotpService totpService;
    private final SecretCipher secretCipher;
    private final AuditLogService auditLogService;

    @Value("${app.security.lockout.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.lockout.minutes:15}")
    private long lockoutMinutes;

    private final SecureRandom random = new SecureRandom();

    /** Hash of a random string, compared against when the username is unknown so timing does not reveal it. */
    private volatile String dummyHash;

    // ------------------------------------------------------------------ login

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthDTO.AuthResult login(AuthDTO.LoginRequest request, String ip) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.getPassword(), dummyHash());
            auditLogService.log(sanitize(request.getUsername()), "LOGIN_FAILED", "User", null, "Unknown user from " + ip);
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        if (isLocked(user) || !user.isActive()) {
            passwordEncoder.matches(request.getPassword(), dummyHash());
            auditLogService.log(user, "LOGIN_REJECTED", "User", user.getId(),
                    user.isActive() ? "Account locked" : "Account deactivated", ip);
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            registerFailure(user, "Bad password", ip);
            throw new BadCredentialsException(GENERIC_FAILURE);
        }

        if (user.isTotpEnabled()) {
            auditLogService.log(user, "LOGIN_PASSWORD_OK", "User", user.getId(), "Awaiting 2FA", ip);
            return new AuthDTO.AuthResult(AuthDTO.SessionResponse.builder()
                    .status("MFA_REQUIRED")
                    .mfaToken(jwtTokenProvider.generateMfaToken(user))
                    .build(), null, null);
        }
        return completeLogin(user, ip);
    }

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthDTO.AuthResult verifyMfa(AuthDTO.MfaVerifyRequest request, String ip) {
        Claims claims = jwtTokenProvider.parseMfa(request.getMfaToken())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_FAILURE));
        User user = userRepository.findByUsername(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_FAILURE));
        Integer tv = claims.get("tv", Integer.class);
        if (tv == null || tv != user.getTokenVersion() || isLocked(user) || !user.isActive() || !user.isTotpEnabled()) {
            throw new BadCredentialsException(GENERIC_FAILURE);
        }

        String code = request.getCode().replace(" ", "").trim();
        boolean ok;
        if (code.matches("\\d{6}")) {
            OptionalLong step = totpService.verify(secretCipher.decrypt(user.getTotpSecretEnc()), code, user.getTotpLastStep());
            step.ifPresent(user::setTotpLastStep);
            ok = step.isPresent();
        } else {
            ok = consumeRecoveryCode(user, code);
        }
        if (!ok) {
            registerFailure(user, "Bad 2FA code", ip);
            throw new BadCredentialsException(GENERIC_FAILURE);
        }
        return completeLogin(user, ip);
    }

    // --------------------------------------------------------------- sessions

    @Override
    @Transactional
    public AuthDTO.AuthResult refresh(String rawRefreshToken, String ip) {
        RefreshTokenService.Issued issued = refreshTokenService.rotate(rawRefreshToken, ip)
                .orElseThrow(() -> new BadCredentialsException("Session expired"));
        User user = issued.user();
        boolean restricted = isRestricted(user);
        return new AuthDTO.AuthResult(sessionBody(user, restricted),
                jwtTokenProvider.generateAccessToken(user, restricted), issued.rawToken());
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthDTO.SessionResponse currentSession(User user) {
        User fresh = userRepository.findById(user.getId()).orElseThrow(() -> new BadCredentialsException(GENERIC_FAILURE));
        return sessionBody(fresh, isRestricted(fresh));
    }

    // --------------------------------------------------------------- password

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthDTO.AuthResult changePassword(User principalUser, AuthDTO.ChangePasswordRequest request, String ip) {
        User user = reload(principalUser);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            registerFailure(user, "Bad current password on change", ip);
            throw new BadRequestException("Current password is incorrect");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("New password must differ from the current one");
        }
        passwordPolicy.validate(request.getNewPassword(), user.getUsername());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        invalidateSessions(user);
        auditLogService.log(user, "PASSWORD_CHANGED", "User", user.getId(), "Password changed; other sessions revoked", ip);
        return newSession(user, ip);
    }

    // -------------------------------------------------------------------- 2FA

    @Override
    @Transactional
    public AuthDTO.TwoFaSetupResponse beginTwoFactorSetup(User principalUser) {
        User user = reload(principalUser);
        if (user.isTotpEnabled()) {
            throw new BadRequestException("Two-factor authentication is already enabled");
        }
        String secret = totpService.generateSecret();
        user.setTotpSecretEnc(secretCipher.encrypt(secret));
        userRepository.save(user);
        return AuthDTO.TwoFaSetupResponse.builder()
                .secret(secret)
                .otpauthUri(totpService.otpauthUri(ISSUER, user.getUsername(), secret))
                .build();
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthDTO.EnableResult enableTwoFactor(User principalUser, String code, String ip) {
        User user = reload(principalUser);
        if (user.isTotpEnabled() || user.getTotpSecretEnc() == null) {
            throw new BadRequestException("Start two-factor setup first");
        }
        OptionalLong step = totpService.verify(secretCipher.decrypt(user.getTotpSecretEnc()), code.trim(), null);
        if (step.isEmpty()) {
            registerFailure(user, "Bad 2FA enrolment code", ip);
            throw new BadRequestException("That code is not valid. Check your authenticator app and try again.");
        }
        user.setTotpEnabled(true);
        user.setTotpLastStep(step.getAsLong());

        recoveryCodeRepository.deleteAllForUser(user.getId());
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String plain = newRecoveryCode();
            codes.add(plain);
            recoveryCodeRepository.save(RecoveryCode.builder().userId(user.getId()).codeHash(sha256(normalize(plain))).build());
        }
        invalidateSessions(user);
        auditLogService.log(user, "TOTP_ENABLED", "User", user.getId(), "Two-factor authentication enabled", ip);
        return new AuthDTO.EnableResult(newSession(user, ip), codes);
    }

    // ---------------------------------------------------------------- helpers

    private AuthDTO.AuthResult completeLogin(User user, String ip) {
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        auditLogService.log(user, "LOGIN", "User", user.getId(), "Successful login", ip);
        return newSession(user, ip);
    }

    private AuthDTO.AuthResult newSession(User user, String ip) {
        userRepository.save(user);
        boolean restricted = isRestricted(user);
        RefreshTokenService.Issued issued = refreshTokenService.startSession(user, ip);
        return new AuthDTO.AuthResult(sessionBody(user, restricted),
                jwtTokenProvider.generateAccessToken(user, restricted), issued.rawToken());
    }

    private AuthDTO.SessionResponse sessionBody(User user, boolean restricted) {
        List<String> actions = new ArrayList<>();
        if (user.isMustChangePassword()) {
            actions.add("CHANGE_PASSWORD");
        }
        if (user.getRole() == Role.ADMIN && !user.isTotpEnabled()) {
            actions.add("ENROLL_2FA");
        }
        return AuthDTO.SessionResponse.builder()
                .status("OK")
                .user(UserDTO.Response.from(user))
                .requiredActions(restricted ? actions : List.of())
                .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
                .build();
    }

    /** Password change or 2FA enrolment pending: the session may only reach the self-service endpoints. */
    private boolean isRestricted(User user) {
        return user.isMustChangePassword() || (user.getRole() == Role.ADMIN && !user.isTotpEnabled());
    }

    private void invalidateSessions(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private void registerFailure(User user, String reason, String ip) {
        int attempts = user.getFailedAttempts() + 1;
        if (attempts >= maxAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
            user.setFailedAttempts(0);
            auditLogService.log(user, "ACCOUNT_LOCKED", "User", user.getId(),
                    "Locked for " + lockoutMinutes + " min after repeated failures", ip);
        } else {
            user.setFailedAttempts(attempts);
        }
        userRepository.save(user);
        auditLogService.log(user, "LOGIN_FAILED", "User", user.getId(), reason, ip);
    }

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private boolean consumeRecoveryCode(User user, String code) {
        return recoveryCodeRepository
                .findByUserIdAndCodeHashAndUsedFalse(user.getId(), sha256(normalize(code)))
                .map(rc -> {
                    rc.setUsed(true);
                    recoveryCodeRepository.save(rc);
                    auditLogService.log(user, "RECOVERY_CODE_USED", "User", user.getId(), "2FA recovery code consumed", null);
                    return true;
                })
                .orElse(false);
    }

    private String newRecoveryCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String normalize(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase();
    }

    private User reload(User principalUser) {
        return userRepository.findById(principalUser.getId())
                .orElseThrow(() -> new BadCredentialsException(GENERIC_FAILURE));
    }

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = passwordEncoder.encode(Long.toHexString(random.nextLong()));
            dummyHash = h;
        }
        return h;
    }

    private static String sanitize(String s) {
        String cleaned = s == null ? "" : s.replaceAll("[^\\p{Alnum}._@-]", "?");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
