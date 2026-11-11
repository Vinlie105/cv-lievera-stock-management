package com.cvlievera.stockmanagement.user;

import com.cvlievera.stockmanagement.common.dto.ApiResponse;
import com.cvlievera.stockmanagement.common.dto.PageResponse;
import com.cvlievera.stockmanagement.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserDTO.Response>>> getAll(
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(userService.getAllUsers(pageable))));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO.Response>> getMe(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getCurrentUser(principal.getUsername())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO.Response>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDTO.Response>> create(
            @Valid @RequestBody UserDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(userService.createUser(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO.Response>> update(
            @PathVariable Long id,
            @Valid @RequestBody UserDTO.UpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateUser(id, request)));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlock(@PathVariable Long id) {
        userService.unlockUser(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @Valid @RequestBody UserDTO.ResetPasswordRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/reset-2fa")
    public ResponseEntity<ApiResponse<Void>> resetTwoFactor(@PathVariable Long id) {
        userService.resetTwoFactor(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
