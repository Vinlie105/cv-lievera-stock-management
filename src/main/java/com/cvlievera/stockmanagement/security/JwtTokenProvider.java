package com.cvlievera.stockmanagement.security;

import com.cvlievera.stockmanagement.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Signs and parses short-lived JWTs. Two token types exist:
 * <ul>
 *   <li><b>access</b> - carried in an httpOnly cookie; may be "restricted" (password change / 2FA enrolment pending)</li>
 *   <li><b>mfa</b> - 5-minute proof that the password step passed; only usable at /api/auth/2fa/verify</li>
 * </ul>
 * Long-lived sessions use opaque, hashed, rotating refresh tokens (see RefreshTokenService), not JWTs.
 */
@Component
public class JwtTokenProvider {

    static final String ISSUER = "cv-lievera-stock";
    static final String TYPE_ACCESS = "access";
    static final String TYPE_MFA = "mfa";
    private static final long MFA_TOKEN_MS = 5 * 60 * 1000L;

    private final String secret;
    private final long accessTokenExpiryMs;
    private SecretKey key;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs) {
        this.secret = secret;
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    @PostConstruct
    public void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(User user, boolean restricted) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("tv", user.getTokenVersion())
                .claim("restricted", restricted)
                .claim("type", TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(key)
                .compact();
    }

    public String generateMfaToken(User user) {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .claim("tv", user.getTokenVersion())
                .claim("type", TYPE_MFA)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + MFA_TOKEN_MS))
                .signWith(key)
                .compact();
    }

    /** Parses and verifies signature, issuer and expiry. Returns empty on any failure. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<Claims> parseAccess(String token) {
        return parse(token).filter(c -> TYPE_ACCESS.equals(c.get("type", String.class)));
    }

    public Optional<Claims> parseMfa(String token) {
        return parse(token).filter(c -> TYPE_MFA.equals(c.get("type", String.class)));
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiryMs / 1000;
    }
}
