package org.ost.user.security;

import org.jspecify.annotations.NonNull;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import org.ost.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record UserPrincipal(User user) implements UserDetails, AuthenticatedPrincipal {

    @Override
    public UserDto toUserDto() {
        return new UserDto(user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.getCreatedAt(), user.getUpdatedAt(), user.getLocale());
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public @NonNull String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
