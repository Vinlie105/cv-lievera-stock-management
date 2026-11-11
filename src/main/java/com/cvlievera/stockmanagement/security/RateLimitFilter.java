package com.cvlievera.stockmanagement.security;

import com.cvlievera.stockmanagement.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Per-IP sliding-window limiter for credential endpoints (login, 2FA, refresh, password change). */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED = Set.of(
            "/api/auth/login", "/api/auth/2fa/verify", "/api/auth/refresh", "/api/auth/password");

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final Clock clock;
    private final int maxRequests;
    private final long windowMs;

    @Autowired
    public RateLimitFilter(ObjectMapper mapper,
                           @Value("${app.security.rate-limit.max-requests:10}") int maxRequests,
                           @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds) {
        this(mapper, Clock.systemUTC(), maxRequests, windowSeconds * 1000);
    }

    RateLimitFilter(ObjectMapper mapper, Clock clock, int maxRequests, long windowMs) {
        this.mapper = mapper;
        this.clock = clock;
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!tryAcquire(ClientIp.of(request))) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowMs / 1000));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(), ApiResponse.error("Too many attempts. Please wait and try again."));
            return;
        }
        chain.doFilter(request, response);
    }

    boolean tryAcquire(String key) {
        long now = clock.millis();
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMs) {
                window.pollFirst();
            }
            if (window.size() >= maxRequests) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /** Drop idle keys so the map cannot grow without bound. */
    @Scheduled(fixedDelay = 300_000)
    void evictIdle() {
        long now = clock.millis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || now - e.getValue().peekLast() >= windowMs;
            }
        });
    }
}
