package com.jansunwai.scheduler;

import com.jansunwai.repository.RevokedTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Revoked tokens are only needed until they expire. */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TokenCleanupJob {
    private final RevokedTokenRepository revoked;

    public TokenCleanupJob(RevokedTokenRepository revoked) {
        this.revoked = revoked;
    }

    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void run() {
        revoked.deleteExpired(Instant.now());
    }
}
