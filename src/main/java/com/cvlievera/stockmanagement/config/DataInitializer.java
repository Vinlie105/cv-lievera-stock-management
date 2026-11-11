package com.cvlievera.stockmanagement.config;

import com.cvlievera.stockmanagement.security.PasswordPolicy;
import com.cvlievera.stockmanagement.user.Role;
import com.cvlievera.stockmanagement.user.User;
import com.cvlievera.stockmanagement.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Creates the first administrator on an empty database. There is no hardcoded default password: it comes
 * from BOOTSTRAP_ADMIN_PASSWORD, or is generated randomly and printed once. Either way the admin must
 * change it (and enrol 2FA) at first login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    @Value("${app.bootstrap.admin-password:}")
    private String configuredPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String password = generated ? randomPassword() : configuredPassword;
        if (!generated) {
            passwordPolicy.validate(password, "admin");
        }
        userRepository.save(User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode(password))
                .fullName("System Administrator")
                .role(Role.ADMIN)
                .active(true)
                .mustChangePassword(true)
                .build());
        if (generated) {
            log.warn("First-run administrator created. username=admin  temporary password={}  "
                    + "(shown once; you must change it at first login)", password);
        } else {
            log.info("First-run administrator created from BOOTSTRAP_ADMIN_PASSWORD (username=admin).");
        }
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("Aa1!");
        for (int i = 0; i < 20; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
