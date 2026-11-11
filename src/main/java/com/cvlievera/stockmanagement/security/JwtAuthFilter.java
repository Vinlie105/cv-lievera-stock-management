package com.cvlievera.stockmanagement.security;

import com.cvlievera.stockmanagement.user.User;
import com.cvlievera.stockmanagement.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/** Authenticates requests from the httpOnly access-token cookie. Bearer headers are deliberately not accepted. */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtTokenProvider::parseAccess)
                .flatMap(this::loadValidPrincipal)
                .ifPresent(principal -> {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
        chain.doFilter(request, response);
    }

    private Optional<UserPrincipal> loadValidPrincipal(Claims claims) {
        Optional<User> user = userRepository.findByUsername(claims.getSubject());
        if (user.isEmpty()) {
            return Optional.empty();
        }
        User u = user.get();
        Integer tokenVersion = claims.get("tv", Integer.class);
        boolean restricted = Boolean.TRUE.equals(claims.get("restricted", Boolean.class));
        UserPrincipal principal = new UserPrincipal(u, restricted);
        boolean valid = tokenVersion != null && tokenVersion == u.getTokenVersion()
                && principal.isEnabled() && principal.isAccountNonLocked();
        return valid ? Optional.of(principal) : Optional.empty();
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (CookieService.ACCESS_COOKIE.equals(c.getName())) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }
}
