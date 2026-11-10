package com.jansunwai.unit;

import com.jansunwai.ai.AiGateway;
import com.jansunwai.ai.AiProvider;
import com.jansunwai.ai.AiProviderException;
import com.jansunwai.support.TestProps;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AiGatewayTest {
    private final AiProvider provider = mock(AiProvider.class);

    @Test
    void returnsValueAndLatency() {
        AiGateway g = new AiGateway(provider, TestProps.withAi(2, 3));
        var r = g.call("test", () -> "ok");
        assertThat(r.value()).isEqualTo("ok");
        assertThat(r.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void slowProviderTimesOutInsteadOfBlocking() {
        AiGateway g = new AiGateway(provider, TestProps.withAi(1, 1));
        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> g.call("slow", () -> {
            try { Thread.sleep(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "late";
        })).isInstanceOf(AiProviderException.class)
           .extracting(e -> ((AiProviderException) e).getCode()).isEqualTo(AiProviderException.Code.TIMEOUT);
        assertThat(System.currentTimeMillis() - start).isLessThan(3_000);
    }

    @Test
    void retryableErrorsAreRetriedUpToTheLimit() {
        AiGateway g = new AiGateway(provider, TestProps.withAi(2, 3));
        AtomicInteger calls = new AtomicInteger();
        var r = g.call("flaky", () -> {
            if (calls.incrementAndGet() < 3) throw new AiProviderException(AiProviderException.Code.RATE_LIMITED, "429");
            return "third time";
        });
        assertThat(r.value()).isEqualTo("third time");
        assertThat(calls).hasValue(3);
    }

    @Test
    void nonRetryableErrorFailsImmediately() {
        AiGateway g = new AiGateway(provider, TestProps.withAi(2, 3));
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> g.call("dims", () -> {
            calls.incrementAndGet();
            throw new AiProviderException(AiProviderException.Code.DIMENSION_MISMATCH, "wrong size");
        })).isInstanceOf(AiProviderException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void unexpectedExceptionBecomesUnavailable() {
        AiGateway g = new AiGateway(provider, TestProps.withAi(2, 1));
        assertThatThrownBy(() -> g.call("boom", () -> { throw new IllegalStateException("network"); }))
                .isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).getCode()).isEqualTo(AiProviderException.Code.UNAVAILABLE);
    }
}
