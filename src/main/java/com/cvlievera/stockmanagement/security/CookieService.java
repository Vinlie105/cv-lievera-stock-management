package com.cvlievera.stockmanagement.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Issues the session cookies: httpOnly, SameSite=Strict, Secure (configurable only for plain-http local dev). */
@Component
public class CookieService {

    public static final String ACCESS_COOKIE = "cvl_at";
    public static final String REFRESH_COOKIE = "cvl_rt";
    static final String REFRESH_PATH = "/api/auth";

    private final boolean secure;

    public CookieService(@Value("${app.security.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public void writeSession(HttpServletResponse response, String accessToken, long accessSeconds,
                             String refreshToken, long refreshSeconds) {
        add(response, build(ACCESS_COOKIE, accessToken, "/", Duration.ofSeconds(accessSeconds)));
        add(response, build(REFRESH_COOKIE, refreshToken, REFRESH_PATH, Duration.ofSeconds(refreshSeconds)));
    }

    public void writeAccess(HttpServletResponse response, String accessToken, long accessSeconds) {
        add(response, build(ACCESS_COOKIE, accessToken, "/", Duration.ofSeconds(accessSeconds)));
    }

    public void clearSession(HttpServletResponse response) {
        add(response, build(ACCESS_COOKIE, "", "/", Duration.ZERO));
        add(response, build(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO));
    }

    public Optional<String> readRefresh(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie c : request.getCookies()) {
            if (REFRESH_COOKIE.equals(c.getName()) && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
