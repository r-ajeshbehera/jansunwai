package com.jansunwai.support;

import com.jansunwai.config.AppProperties;

import java.math.BigDecimal;
import java.util.List;

/** Builds AppProperties for plain unit tests (no Spring context). Values mirror application.yml defaults. */
public final class TestProps {
    private TestProps() { }

    public static AppProperties withAi(int timeoutSeconds, int maxAttempts) {
        return new AppProperties(
                new AppProperties.Jwt("dW5pdC10ZXN0LW9ubHktc2VjcmV0LXVuaXQtdGVzdC1vbmx5LXNlY3JldC0xMjM0", 60),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.RateLimit(10, 5, 30),
                new AppProperties.Uploads("/tmp/jansunwai-unit", 5_242_880, 5),
                new AppProperties.Ai("mock", timeoutSeconds, maxAttempts, 0.60, 768, BigDecimal.ZERO, BigDecimal.ZERO),
                duplicates(),
                new AppProperties.Sla(24, 7),
                new AppProperties.Jobs(false));
    }

    public static AppProperties defaults() {
        return withAi(5, 3);
    }

    public static AppProperties.Duplicates duplicates() {
        return new AppProperties.Duplicates(0.40, 0.55, 500, 30, 5);
    }
}
