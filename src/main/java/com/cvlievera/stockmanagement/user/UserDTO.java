package com.cvlievera.stockmanagement.user;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

public class UserDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may only contain letters, digits, dots, hyphens, and underscores")
        private String username;

        /** Temporary password; policy is enforced in the service and the user must change it at first login. */
        @NotBlank(message = "Password is required")
        @Size(max = 128)
        private String password;

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        private String fullName;

        @Email(message = "Email must be a valid email address")
        @Size(max = 150)
        private String email;

        @NotNull(message = "Role is required")
        private Role role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        private String fullName;

        @Email(message = "Email must be a valid email address")
        @Size(max = 150)
        private String email;

        @NotNull(message = "Role is required")
        private Role role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private Role role;
        private boolean active;
        private LocalDateTime lastLoginAt;
        private LocalDateTime lockedUntil;
        private boolean totpEnabled;
        private boolean mustChangePassword;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(User user) {
            return Response.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .active(user.isActive())
                    .lastLoginAt(user.getLastLoginAt())
                    .lockedUntil(user.getLockedUntil())
                    .totpEnabled(user.isTotpEnabled())
                    .mustChangePassword(user.isMustChangePassword())
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString(exclude = "newPassword")
    public static class ResetPasswordRequest {
        @NotBlank(message = "Temporary password is required")
        @Size(max = 128)
        private String newPassword;
    }
}
