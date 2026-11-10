package com.jansunwai.scheduler;

import com.jansunwai.service.EscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Every 5 minutes: deadline warnings, level-1/level-2 escalations, auto-close. Idempotent (see EscalationService). */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SlaMonitorJob {
    private static final Logger log = LoggerFactory.getLogger(SlaMonitorJob.class);
    private final EscalationService escalations;

    public SlaMonitorJob(EscalationService escalations) {
        this.escalations = escalations;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT20S")
    public void run() {
        try {
            escalations.runOnce(Instant.now());
        } catch (RuntimeException e) {
            log.error("SLA monitor failed; it will run again in 5 minutes", e);
        }
    }
}
