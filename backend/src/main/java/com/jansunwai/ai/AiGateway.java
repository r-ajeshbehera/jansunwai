package com.jansunwai.ai;

import com.jansunwai.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Every AI call goes through here: hard timeout, limited retries with backoff for retryable errors, latency
 * measurement. A slow or broken provider can never block a request thread forever.
 */
@Component
public class AiGateway {
    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final AiProvider provider;
    private final int timeoutSeconds;
    private final int maxAttempts;
    private final ExecutorService callPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ai-call");
        t.setDaemon(true);
        return t;
    });

    public AiGateway(AiProvider provider, AppProperties props) {
        this.provider = provider;
        this.timeoutSeconds = props.ai().timeoutSeconds();
        this.maxAttempts = Math.max(1, props.ai().maxAttempts());
    }

    public record Timed<T>(T value, int latencyMs) { }

    public AiProvider provider() { return provider; }

    public <T> Timed<T> call(String operation, Supplier<T> work) {
        AiProviderException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long start = System.nanoTime();
            Future<T> future = callPool.submit(work::get);
            try {
                T value = future.get(timeoutSeconds, TimeUnit.SECONDS);
                return new Timed<>(value, (int) ((System.nanoTime() - start) / 1_000_000));
            } catch (TimeoutException e) {
                future.cancel(true);
                last = new AiProviderException(AiProviderException.Code.TIMEOUT, operation + " timed out after " + timeoutSeconds + "s");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                last = cause instanceof AiProviderException ape ? ape
                        : new AiProviderException(AiProviderException.Code.UNAVAILABLE, operation + " failed: "
                          + cause.getClass().getSimpleName(), cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProviderException(AiProviderException.Code.UNAVAILABLE, "interrupted");
            }
            log.warn("AI {} attempt {}/{} failed: {}", operation, attempt, maxAttempts, last.getCode());
            if (!last.isRetryable() || attempt == maxAttempts) break;
            sleep(250L * attempt);              // simple linear backoff
        }
        throw last;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdown() {
        callPool.shutdownNow();
    }
}
