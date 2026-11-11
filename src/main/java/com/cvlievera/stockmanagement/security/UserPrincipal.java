package com.cvlievera.stockmanagement.security;

import com.cvlievera.stockmanagement.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Authority model: a fully authenticated user holds ROLE_ADMIN / ROLE_STAFF. A user who still has to change
 * their password or enrol 2FA holds only AUTH_RESTRICTED, which unlocks nothing except the self-service
 * endpoints listed in SecurityConfig.
 */
public class UserPrincipal implements UserDetails {

    public static final String RESTRICTED = "AUTH_RESTRICTED";

    private final User user;
    private final boolean restricted;

    public UserPrincipal(User user) {
        this(user, false);
    }

    public UserPrincipal(User user, boolean restricted) {
        this.user = user;
        this.restricted = restricted;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return restricted
                ? List.of(new SimpleGrantedAuthority(RESTRICTED))
                : List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getLockedUntil() == null || user.getLockedUntil().isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    public User getUser() {
        return user;
    }

    public boolean isRestricted() {
        return restricted;
    }
}
