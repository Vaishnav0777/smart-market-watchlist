package com.smartwatch.user.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated account. The password hash is intentionally absent.
 */
public final class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final boolean enabled;

    public UserPrincipal(UUID id, String email, boolean enabled) {
        this.id = id;
        this.email = email;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
