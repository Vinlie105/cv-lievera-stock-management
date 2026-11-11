package com.cvlievera.stockmanagement.auth;

import com.cvlievera.stockmanagement.common.dto.ApiResponse;
import com.cvlievera.stockmanagement.security.ClientIp;
import com.cvlievera.stockmanagement.security.CookieService;
import com.cvlievera.stockmanagement.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDTO.SessionResponse>> login(
            @Valid @RequestBody AuthDTO.LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        return respond(authService.login(request, ClientIp.of(httpRequest)), response);
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<AuthDTO.SessionResponse>> verifyMfa(
            @Valid @RequestBody AuthDTO.MfaVerifyRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        return respond(authService.verifyMfa(request, ClientIp.of(httpRequest)), response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthDTO.SessionResponse>> refresh(
            HttpServletRequest httpRequest, HttpServletResponse response) {
        String raw = cookieService.readRefresh(httpRequest)
                .orElseThrow(() -> new BadCredentialsException("No session"));
        try {
            return respond(authService.refresh(raw, ClientIp.of(httpRequest)), response);
        } catch (BadCredentialsException e) {
            cookieService.clearSession(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest, HttpServletResponse response) {
        authService.logout(cookieService.readRefresh(httpRequest).orElse(null));
        cookieService.clearSession(response);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthDTO.SessionResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(authService.currentSession(principal.getUser())));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<AuthDTO.SessionResponse>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AuthDTO.ChangePasswordRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        return respond(authService.changePassword(principal.getUser(), request, ClientIp.of(httpRequest)), response);
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<ApiResponse<AuthDTO.TwoFaSetupResponse>> setupTwoFactor(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(authService.beginTwoFactorSetup(principal.getUser())));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<ApiResponse<AuthDTO.TwoFaEnableResponse>> enableTwoFactor(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AuthDTO.TwoFaEnableRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthDTO.EnableResult result = authService.enableTwoFactor(
                principal.getUser(), request.getCode(), ClientIp.of(httpRequest));
        writeCookies(result.auth(), response);
        return ResponseEntity.ok(ApiResponse.ok(AuthDTO.TwoFaEnableResponse.builder()
                .recoveryCodes(result.recoveryCodes())
                .session(result.auth().body())
                .build()));
    }

    private ResponseEntity<ApiResponse<AuthDTO.SessionResponse>> respond(
            AuthDTO.AuthResult result, HttpServletResponse response) {
        writeCookies(result, response);
        return ResponseEntity.ok(ApiResponse.ok(result.body()));
    }

    private void writeCookies(AuthDTO.AuthResult result, HttpServletResponse response) {
        if (result.hasSession()) {
            cookieService.writeSession(response, result.accessToken(), result.body().getExpiresIn(),
                    result.refreshToken(), refreshTokenService.idleSeconds());
        }
    }
}
