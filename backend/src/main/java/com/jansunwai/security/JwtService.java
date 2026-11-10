package com.jansunwai.security;

import com.jansunwai.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** HS256 JWTs: subject = user id, plus role and a unique id (jti) so logout can revoke one token. */
@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(AppProperties props) {
        byte[] bytes = Decoders.BASE64.decode(props.jwt().secret());
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be a Base64 value of at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMinutes = props.jwt().expirationMinutes();
    }

    public record IssuedToken(String token, Instant expiresAt) { }

    public record TokenData(Long userId, String jti, Instant expiresAt) { }

    public IssuedToken issue(AppUserPrincipal user) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(String.valueOf(user.id()))
                .id(UUID.randomUUID().toString())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, exp);
    }

    /** Empty when the signature is wrong, the token expired or it is malformed. */
    public Optional<TokenData> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new TokenData(Long.valueOf(c.getSubject()), c.getId(), c.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
