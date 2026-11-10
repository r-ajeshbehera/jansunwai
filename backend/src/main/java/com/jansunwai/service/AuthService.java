package com.jansunwai.service;

import com.jansunwai.dto.request.LoginRequest;
import com.jansunwai.dto.request.RegisterRequest;
import com.jansunwai.dto.response.LoginResponse;
import com.jansunwai.dto.response.ProfileResponse;
import com.jansunwai.entity.RevokedToken;
import com.jansunwai.entity.Role;
import com.jansunwai.entity.User;
import com.jansunwai.exception.BusinessRuleException;
import com.jansunwai.repository.RevokedTokenRepository;
import com.jansunwai.repository.UserRepository;
import com.jansunwai.security.AppUserPrincipal;
import com.jansunwai.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwt;
    private final UserRepository users;
    private final RevokedTokenRepository revoked;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwt, UserRepository users,
                       RevokedTokenRepository revoked, PasswordEncoder encoder, AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.jwt = jwt;
        this.users = users;
        this.revoked = revoked;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** Public sign-up ALWAYS creates a CITIZEN; officers and admins are created by an administrator. */
    @Transactional
    public ProfileResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new BusinessRuleException("EMAIL_TAKEN", "An account with this email already exists");
        User u = users.save(new User(req.fullName().trim(), email, req.phone(), encoder.encode(req.password()), Role.CITIZEN, null));
        audit.log(u.getId(), "REGISTERED", "USER", u.getId(), null);
        return ProfileResponse.from(u);
    }

    /** Wrong email and wrong password give the same 401 (BadCredentialsException). */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        var auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.email().trim(), req.password()));
        AppUserPrincipal p = (AppUserPrincipal) auth.getPrincipal();
        JwtService.IssuedToken token = jwt.issue(p);
        audit.log(p.id(), "LOGIN", "USER", p.id(), null);
        return new LoginResponse(token.token(), "Bearer", token.expiresAt(), ProfileResponse.from(users.findWithDepartment(p.id()).orElseThrow()));
    }

    /** JWTs cannot be deleted, so the token id is deny-listed until it would expire anyway. */
    @Transactional
    public void logout(String jti, Instant expiresAt, Long userId) {
        if (jti == null || revoked.existsById(jti)) return;
        revoked.save(new RevokedToken(jti, expiresAt));
        audit.log(userId, "LOGOUT", "USER", userId, null);
    }
}
