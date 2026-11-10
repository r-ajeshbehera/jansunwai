package com.jansunwai.scheduler;

import com.jansunwai.service.WardSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Monday 06:10 IST: summaries for the week that just ended. Upsert per (ward, week), so re-runs are harmless. */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WeeklySummaryJob {
    private static final Logger log = LoggerFactory.getLogger(WeeklySummaryJob.class);
    private final WardSummaryService summaries;

    public WeeklySummaryJob(WardSummaryService summaries) {
        this.summaries = summaries;
    }

    @Scheduled(cron = "0 10 6 * * MON", zone = "Asia/Kolkata")
    public void run() {
        try {
            int n = summaries.generateAll(LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1)).size();
            log.info("Weekly ward summaries generated: {}", n);
        } catch (RuntimeException e) {
            log.error("Weekly summary job failed", e);
        }
    }
}
