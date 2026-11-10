package com.jansunwai.controller;

import com.jansunwai.dto.request.*;
import com.jansunwai.dto.response.LoginResponse;
import com.jansunwai.dto.response.ProfileResponse;
import com.jansunwai.security.CurrentUser;
import com.jansunwai.security.JwtAuthenticationFilter;
import com.jansunwai.service.AuthService;
import com.jansunwai.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api")
@Tag(name = "Authentication & profile")
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Citizen self-registration (always creates a CITIZEN account)")
    public ProfileResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in and receive a JWT (rate-limited)")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.logout((String) request.getAttribute(JwtAuthenticationFilter.JTI_ATTRIBUTE),
                (Instant) request.getAttribute(JwtAuthenticationFilter.EXP_ATTRIBUTE), CurrentUser.get().id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/me")
    public ProfileResponse me() {
        return users.me(CurrentUser.get());
    }

    @PutMapping("/users/me")
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return users.updateProfile(req, CurrentUser.get());
    }

    @PatchMapping("/users/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        users.changePassword(req, CurrentUser.get());
        return ResponseEntity.noContent().build();
    }
}
