package com.cvlievera.stockmanagement.auth;

import com.cvlievera.stockmanagement.user.UserDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

public class AuthDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString(exclude = "password")
    public static class LoginRequest {
        @NotBlank(message = "Username is required")
        @Size(max = 100)
        private String username;

        @NotBlank(message = "Password is required")
        @Size(max = 128)
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MfaVerifyRequest {
        @NotBlank(message = "Verification token is required")
        private String mfaToken;

        @NotBlank(message = "Code is required")
        @Size(max = 20)
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString(exclude = {"currentPassword", "newPassword"})
    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        @Size(max = 128)
        private String currentPassword;

        @NotBlank(message = "New password is required")
        @Size(max = 128)
        private String newPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TwoFaEnableRequest {
        @NotBlank(message = "Code is required")
        @Size(max = 10)
        private String code;
    }

    /** What the browser receives. Tokens are never in the body; they travel only in httpOnly cookies. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SessionResponse {
        /** OK or MFA_REQUIRED */
        private String status;
        private String mfaToken;
        private UserDTO.Response user;
        /** Non-empty while the session is restricted: CHANGE_PASSWORD, ENROLL_2FA */
        private List<String> requiredActions;
        private Long expiresIn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TwoFaSetupResponse {
        private String secret;
        private String otpauthUri;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TwoFaEnableResponse {
        private List<String> recoveryCodes;
        private SessionResponse session;
    }

    public record EnableResult(AuthResult auth, List<String> recoveryCodes) {
    }

    /** Service result: response body plus the raw tokens for the controller to put in cookies. */
    public record AuthResult(SessionResponse body, String accessToken, String refreshToken) {
        public boolean hasSession() {
            return accessToken != null;
        }
    }
}
