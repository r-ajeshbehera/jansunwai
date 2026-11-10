package com.jansunwai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/** Every "app.*" setting in one type-safe, immutable tree. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, RateLimit rateLimit, Uploads uploads, Ai ai, Duplicates duplicates,
                            Sla sla, Jobs jobs) {

    public record Jwt(String secret, long expirationMinutes) { }

    public record Cors(List<String> allowedOrigins) { }

    public record RateLimit(int authPerMinute, int submitPerMinute, int publicPerMinute) { }

    public record Uploads(String directory, long maxBytes, int maxPerComplaint) { }

    /**
     * provider: "mock" (offline, deterministic, default) or "spring-ai" (Gemini / Ollama / OpenAI through Spring AI).
     * minConfidence: below this, the AI suggestion is not used for routing and an officer must classify.
     */
    public record Ai(String provider, int timeoutSeconds, int maxAttempts, double minConfidence, int embeddingDimensions,
                     BigDecimal inputCostPer1k, BigDecimal outputCostPer1k) { }

    /** Duplicate candidate rules: semantic similarity + distance + same category + time window. */
    public record Duplicates(double minSemantic, double minTotal, int radiusMeters, int windowDays, int maxCandidates) { }

    /** Second escalation level is raised this many hours after the deadline. Auto-close resolved complaints after N days. */
    public record Sla(int level2AfterHours, int autoCloseDays) { }

    public record Jobs(boolean enabled) { }
}
