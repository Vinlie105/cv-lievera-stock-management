package com.cvlievera.stockmanagement.security;

import com.cvlievera.stockmanagement.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;
    private static final Set<String> COMMON = Set.of(
            "password1234", "123456789012", "qwertyuiop12", "administrator", "welcome12345", "letmein12345");

    public void validate(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BadRequestException("Password must not exceed " + MAX_LENGTH + " characters");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean symbol = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        int classes = (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
        if (classes < 3) {
            throw new BadRequestException("Password must mix at least 3 of: uppercase, lowercase, digits, symbols");
        }
        String lowered = password.toLowerCase();
        if (COMMON.contains(lowered) || (username != null && lowered.contains(username.toLowerCase()))) {
            throw new BadRequestException("Password is too easy to guess");
        }
    }
}
