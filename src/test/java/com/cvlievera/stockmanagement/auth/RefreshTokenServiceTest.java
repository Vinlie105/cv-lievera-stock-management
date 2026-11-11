package com.cvlievera.stockmanagement.auth;

import com.cvlievera.stockmanagement.user.Role;
import com.cvlievera.stockmanagement.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repo;
    private final List<RefreshToken> store = new ArrayList<>();
    private final User user = User.builder().id(1L).username("u").role(Role.STAFF).active(true).build();
    private long nowMs = 1_700_000_000_000L;

    private final Clock clock = new Clock() {
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(nowMs); }
    };

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            if (!store.contains(t)) {
                store.add(t);
            }
            return t;
        });
        when(repo.findByTokenHashForUpdate(anyString())).thenAnswer(inv ->
                store.stream().filter(t -> t.getTokenHash().equals(inv.getArgument(0))).findFirst());
        when(repo.revokeFamily(anyString())).thenAnswer(inv -> {
            store.stream().filter(t -> t.getFamilyId().equals(inv.getArgument(0))).forEach(t -> t.setRevoked(true));
            return 1;
        });
        service = new RefreshTokenService(repo, clock, Duration.ofMinutes(30), Duration.ofHours(12));
    }

    @Test
    void storesOnlyTheHashNeverTheRawToken() {
        String raw = service.startSession(user, "1.1.1.1").rawToken();
        assertEquals(1, store.size());
        assertNotEquals(raw, store.get(0).getTokenHash());
        assertEquals(RefreshTokenService.hash(raw), store.get(0).getTokenHash());
    }

    @Test
    void rotationIssuesNewTokenInSameFamilyAndMarksOldUsed() {
        String first = service.startSession(user, "ip").rawToken();
        Optional<RefreshTokenService.Issued> next = service.rotate(first, "ip");
        assertTrue(next.isPresent());
        assertNotEquals(first, next.get().rawToken());
        assertTrue(store.get(0).isUsed());
        assertEquals(store.get(0).getFamilyId(), store.get(1).getFamilyId());
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeFamily() {
        String first = service.startSession(user, "ip").rawToken();
        String second = service.rotate(first, "ip").orElseThrow().rawToken();

        assertTrue(service.rotate(first, "attacker").isEmpty(), "replay must fail");
        assertTrue(service.rotate(second, "ip").isEmpty(), "legitimate descendant is revoked too");
        assertTrue(store.stream().allMatch(RefreshToken::isRevoked));
    }

    @Test
    void idleTimeoutExpiresTheSession() {
        String raw = service.startSession(user, "ip").rawToken();
        nowMs += Duration.ofMinutes(31).toMillis();
        assertTrue(service.rotate(raw, "ip").isEmpty());
    }

    @Test
    void absoluteLifetimeCapsASessionKeptAliveByActivity() {
        String raw = service.startSession(user, "ip").rawToken();
        for (int i = 0; i < 26; i++) { // 26 x 29min of "activity" stays under the idle limit each time
            nowMs += Duration.ofMinutes(29).toMillis();
            Optional<RefreshTokenService.Issued> r = service.rotate(raw, "ip");
            if (r.isEmpty()) {
                return; // cut off by the 12h absolute cap, as intended
            }
            raw = r.get().rawToken();
        }
        fail("session survived past the absolute cap");
    }

    @Test
    void deactivatedUserCannotRefresh() {
        String raw = service.startSession(user, "ip").rawToken();
        user.setActive(false);
        assertTrue(service.rotate(raw, "ip").isEmpty());
    }

    @Test
    void unknownTokenIsRejected() {
        assertTrue(service.rotate("not-a-real-token", "ip").isEmpty());
        verify(repo, never()).revokeFamily(anyString());
    }
}
