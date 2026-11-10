package com.jansunwai.security;

import com.jansunwai.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String email) {
        return users.findByEmailIgnoreCase(email).map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }

    @Transactional(readOnly = true)
    public AppUserPrincipal loadById(Long id) {
        return users.findWithDepartment(id).map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }
}
