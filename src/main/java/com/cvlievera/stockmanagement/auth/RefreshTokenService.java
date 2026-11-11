package com.cvlievera.stockmanagement.auth;

import com.cvlievera.stockmanagement.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 * <ul>
 *   <li>Idle timeout: each token is valid for {@code idleMinutes} from issue (UC-03 session timeout).</li>
 *   <li>Absolute cap: a login chain ("family") can never outlive {@code absoluteHours}.</li>
 *   <li>Presenting an already-used token revokes the whole family (the token was stolen or replayed).</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.security.session.idle-minutes:30}") long idleMinutes,
                               @Value("${app.security.session.absolute-hours:12}") long absoluteHours) {
        this(repository, Clock.systemDefaultZone(), Duration.ofMinutes(idleMinutes), Duration.ofHours(absoluteHours));
    }

    RefreshTokenService(RefreshTokenRepository repository, Clock clock, Duration idle, Duration absolute) {
        this.repository = repository;
        this.clock = clock;
        this.idleTimeout = idle;
        this.absoluteTimeout = absolute;
    }

    public long idleSeconds() {
        return idleTimeout.toSeconds();
    }

    /** Result of issuing/rotating: the raw token (for the cookie) and its owner. */
    public record Issued(String rawToken, User user) {
    }

    @Transactional
    public Issued startSession(User user, String ip) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new Issued(save(user, UUID.randomUUID().toString(), now, now, ip), user);
    }

    /**
     * Validates and rotates. Runs in its own transaction so that a reuse-triggered family revocation is
     * committed even though the caller then rejects the request.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Issued> rotate(String rawToken, String ip) {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<RefreshToken> found = repository.findByTokenHashForUpdate(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken current = found.get();

        if (current.isUsed() || current.isRevoked()) {
            repository.revokeFamily(current.getFamilyId()); // reuse detected
            return Optional.empty();
        }
        boolean expired = !current.getExpiresAt().isAfter(now)
                || !current.getFamilyStart().plus(absoluteTimeout).isAfter(now);
        User user = current.getUser();
        if (expired || !user.isActive()) {
            current.setRevoked(true);
            repository.save(current);
            return Optional.empty();
        }

        current.setUsed(true);
        repository.save(current);
        return Optional.of(new Issued(save(user, current.getFamilyId(), current.getFamilyStart(), now, ip), user));
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId);
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpired(LocalDateTime.now(clock).minusDays(1));
    }

    private String save(User user, String familyId, LocalDateTime familyStart, LocalDateTime now, String ip) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime idleExpiry = now.plus(idleTimeout);
        LocalDateTime absoluteExpiry = familyStart.plus(absoluteTimeout);
        repository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .familyId(familyId)
                .familyStart(familyStart)
                .expiresAt(idleExpiry.isBefore(absoluteExpiry) ? idleExpiry : absoluteExpiry)
                .createdAt(now)
                .ipAddress(ip)
                .build());
        return raw;
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
