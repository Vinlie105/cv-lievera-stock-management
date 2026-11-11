package com.cvlievera.stockmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** The PWA is served from this same origin, so everything can be locked to 'self' (no CDNs, no inline code). */
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; manifest-src 'self'; worker-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtAuthFilter,
                                           RateLimitFilter rateLimitFilter,
                                           @Value("${app.security.cookie-secure:true}") boolean cookieSecure)
            throws Exception {

        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(c -> c.sameSite("Strict").secure(cookieSecure).path("/"));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // stateless app: do NOT rotate/clear the CSRF cookie every time JwtAuthFilter authenticates a request
                        .sessionAuthenticationStrategy(new org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy())
                        // credential-submission steps run before any session exists; SameSite=Strict + rate limits cover them
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/2fa/verify"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> { })
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/2fa/verify",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // reachable while the session is still "restricted" (forced password change / 2FA enrolment)
                        .requestMatchers("/api/auth/me", "/api/auth/password",
                                "/api/auth/2fa/setup", "/api/auth/2fa/enable").authenticated()
                        .requestMatchers("/api/users/**", "/api/audit-logs/**").hasRole("ADMIN")
                        // STAFF = day-to-day operations: read everything, record stock movements. Everything else is ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/transactions").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll() // static PWA assets only; there are no other non-API routes
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterAfter(new ApiNoStoreFilter(), org.springframework.security.web.csrf.CsrfFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Forces the deferred CSRF token to be generated so the XSRF-TOKEN cookie reaches the browser. */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }

    /** API responses carry personal/financial data and must never be cached by browsers or proxies. */
    static class ApiNoStoreFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getRequestURI().startsWith("/api/")) {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Pragma", "no-cache");
            }
            chain.doFilter(request, response);
        }
    }
}
