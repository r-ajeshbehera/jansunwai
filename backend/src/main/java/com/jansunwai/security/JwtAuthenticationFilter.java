package com.jansunwai.security;

import com.jansunwai.repository.RevokedTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Valid, non-revoked Bearer token -> user loaded fresh from the DB (deactivation and role changes apply at once) ->
 * SecurityContext. Otherwise the request continues anonymously and protected URLs answer 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String JTI_ATTRIBUTE = "jwt.jti";
    public static final String EXP_ATTRIBUTE = "jwt.exp";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final RevokedTokenRepository revokedTokens;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService,
                                   RevokedTokenRepository revokedTokens) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.revokedTokens = revokedTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parse(header.substring(7)).ifPresent(data -> {
                if (revokedTokens.existsById(data.jti())) return;
                try {
                    AppUserPrincipal user = userDetailsService.loadById(data.userId());
                    if (!user.isEnabled()) return;
                    var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute(JTI_ATTRIBUTE, data.jti());
                    request.setAttribute(EXP_ATTRIBUTE, data.expiresAt());
                } catch (UsernameNotFoundException ignored) {
                    // token of a deleted user: stay anonymous
                }
            });
        }
        chain.doFilter(request, response);
    }
}
