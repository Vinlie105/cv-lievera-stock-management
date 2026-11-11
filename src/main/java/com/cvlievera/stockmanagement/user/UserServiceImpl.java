package com.cvlievera.stockmanagement.user;

import com.cvlievera.stockmanagement.audit.Auditable;
import com.cvlievera.stockmanagement.auth.RecoveryCodeRepository;
import com.cvlievera.stockmanagement.auth.RefreshTokenService;
import com.cvlievera.stockmanagement.security.PasswordPolicy;
import com.cvlievera.stockmanagement.exception.BadRequestException;
import com.cvlievera.stockmanagement.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshTokenService refreshTokenService;
    private final RecoveryCodeRepository recoveryCodeRepository;

    @Override
    public Page<UserDTO.Response> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public UserDTO.Response getUserById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public UserDTO.Response getCurrentUser(String username) {
        return toResponse(userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username)));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE_USER", entityType = "User")
    public UserDTO.Response createUser(UserDTO.CreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username '" + request.getUsername() + "' is already taken");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email '" + request.getEmail() + "' is already in use");
        }
        passwordPolicy.validate(request.getPassword(), request.getUsername());
        User user = User.builder()
                .username(request.getUsername())
                .mustChangePassword(true)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .role(request.getRole())
                .active(true)
                .build();
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE_USER", entityType = "User", entityIdParam = 0)
    public UserDTO.Response updateUser(Long id, UserDTO.UpdateRequest request) {
        User user = findById(id);
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        if (user.getRole() != request.getRole()) {
            user.setRole(request.getRole());
            endSessions(user);
        }
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE_USER", entityType = "User", entityIdParam = 0)
    public void deactivateUser(Long id) {
        User user = findById(id);
        user.setActive(false);
        endSessions(user);
    }

    @Override
    @Transactional
    @Auditable(action = "UNLOCK_USER", entityType = "User", entityIdParam = 0)
    public void unlockUser(Long id) {
        User user = findById(id);
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        userRepository.save(user);
    }

    @Override
    @Transactional
    @Auditable(action = "RESET_PASSWORD", entityType = "User", entityIdParam = 0)
    public void resetPassword(Long id, String temporaryPassword) {
        User user = findById(id);
        passwordPolicy.validate(temporaryPassword, user.getUsername());
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        endSessions(user);
    }

    @Override
    @Transactional
    @Auditable(action = "RESET_2FA", entityType = "User", entityIdParam = 0)
    public void resetTwoFactor(Long id) {
        User user = findById(id);
        user.setTotpEnabled(false);
        user.setTotpSecretEnc(null);
        user.setTotpLastStep(null);
        recoveryCodeRepository.deleteAllForUser(user.getId());
        endSessions(user);
    }

    /** Invalidates outstanding access tokens (token version) and every refresh token. */
    private void endSessions(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE_USER", entityType = "User", entityIdParam = 0)
    public void activateUser(Long id) {
        User user = findById(id);
        user.setActive(true);
        userRepository.save(user);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    UserDTO.Response toResponse(User user) {
        return UserDTO.Response.from(user);
    }
}
