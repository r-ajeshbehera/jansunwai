package com.jansunwai.security;

import com.jansunwai.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window limiter per client IP and bucket (no Redis): login/register, complaint submission (also stops AI
 * cost abuse: every complaint triggers an AI call) and public tracking. Counters are per instance (documented limit).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final int authLimit;
    private final int submitLimit;
    private final int publicLimit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(AppProperties props) {
        this(props.rateLimit().authPerMinute(), props.rateLimit().submitPerMinute(), props.rateLimit().publicPerMinute(),
                Clock.systemUTC());
    }

    RateLimitFilter(int authLimit, int submitLimit, int publicLimit, Clock clock) {
        this.authLimit = authLimit;
        this.submitLimit = submitLimit;
        this.publicLimit = publicLimit;
        this.clock = clock;
    }

    private record Window(long minute, AtomicInteger count) { }

    static String bucket(String method, String uri) {
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/register")) return "auth";
        if ("POST".equals(method) && uri.equals("/api/complaints")) return "submit";
        if (uri.startsWith("/api/public/") || uri.startsWith("/api/complaints/track/")) return "public";
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return bucket(request.getMethod(), request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket = bucket(request.getMethod(), request.getRequestURI());
        int limit = switch (bucket) {
            case "auth" -> authLimit;
            case "submit" -> submitLimit;
            default -> publicLimit;
        };
        if (!tryAcquire(bucket + ":" + clientIp(request), limit)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again in a minute.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    boolean tryAcquire(String key, int limit) {
        long minute = clock.millis() / 60_000;
        Window w = windows.compute(key, (k, old) -> (old == null || old.minute() != minute)
                ? new Window(minute, new AtomicInteger()) : old);
        if (windows.size() > 50_000) windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        return w.count().incrementAndGet() <= limit;
    }

    /** Behind Nginx the client IP arrives in X-Forwarded-For; Nginx overwrites it, so it cannot be spoofed. */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
