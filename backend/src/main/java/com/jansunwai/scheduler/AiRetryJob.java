package com.jansunwai.scheduler;

import com.jansunwai.config.AppProperties;
import com.jansunwai.entity.Complaint;
import com.jansunwai.entity.ComplaintStatus;
import com.jansunwai.repository.ComplaintRepository;
import com.jansunwai.service.AiPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/**
 * Picks up complaints still waiting for AI (provider was down, server restarted mid-analysis, demo data) and
 * embeds complaints that have no vector yet. Bounded by app.ai.max-attempts per complaint.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiRetryJob {
    private static final Logger log = LoggerFactory.getLogger(AiRetryJob.class);
    private final ComplaintRepository complaints;
    private final AiPipelineService pipeline;
    private final int maxAttempts;

    public AiRetryJob(ComplaintRepository complaints, AiPipelineService pipeline, AppProperties props) {
        this.complaints = complaints;
        this.pipeline = pipeline;
        this.maxAttempts = props.ai().maxAttempts();
    }

    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT5S")
    public void run() {
        try {
            // Back-fill vectors first so that duplicate detection for the complaints processed below can see older ones.
            int embedded = pipeline.backfillEmbeddings(100);
            Instant before = Instant.now().minus(Duration.ofSeconds(90));
            int n = 0;
            for (Complaint c : complaints.findWaitingForAi(EnumSet.of(ComplaintStatus.SUBMITTED, ComplaintStatus.AI_ANALYSIS_PENDING),
                    before, maxAttempts)) {
                pipeline.process(c.getId());
                n++;
            }
            if (n + embedded > 0) log.info("AI retry: {} complaint(s) processed, {} embedding(s) back-filled", n, embedded);
        } catch (RuntimeException e) {
            log.error("AI retry job failed; it will run again", e);
        }
    }
}
