package com.jansunwai.security;

import com.jansunwai.entity.Role;
import com.jansunwai.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The logged-in user as Spring Security sees it. Carries the department so services can scope queries. */
public record AppUserPrincipal(Long id, String email, String fullName, String passwordHash, Role role, Long departmentId,
                               boolean active) implements UserDetails {

    public static AppUserPrincipal from(User u) {
        return new AppUserPrincipal(u.getId(), u.getEmail(), u.getFullName(), u.getPasswordHash(), u.getRole(),
                u.getDepartmentId(), u.isActive());
    }

    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isCitizen() { return role == Role.CITIZEN; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
}
