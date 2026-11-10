package com.jansunwai.it;

import com.jansunwai.service.EscalationService;
import com.jansunwai.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The scheduler is switched off in tests; we call EscalationService.runOnce directly (that is what the job does). */
class EscalationIT extends IntegrationTestBase {
    @Autowired
    EscalationService escalations;

    private long approved(String title) {
        return approvedComplaint(title, "Deep pothole on the road, two-wheelers fall at night.", ROADS, "PUBLIC_WORKS",
                "ROAD_DAMAGE", "HIGH");
    }

    private void moveDeadline(long id, String acceptedAgo, String dueAgo) {
        jdbc.update("update complaints set accepted_at = now() - cast(? as interval), sla_due_at = now() - cast(? as interval), "
                + "version = version + 1 where id = ?", acceptedAgo, dueAgo, id);
    }

    private int escalationRows(long id) {
        return jdbc.queryForObject("select count(*) from escalation_history where complaint_id = ?", Integer.class, id);
    }

    private int level(long id) {
        return jdbc.queryForObject("select escalation_level from complaints where id = ?", Integer.class, id);
    }

    @Test
    void overdueComplaintIsEscalatedExactlyOnceNoMatterHowOftenTheJobRuns() {
        long id = approved("Pothole near hospital gate");
        moveDeadline(id, "30 hours", "2 hours");
        escalations.runOnce(Instant.now());
        escalations.runOnce(Instant.now());
        escalations.runOnce(Instant.now());
        assertThat(escalationRows(id)).isEqualTo(1);
        assertThat(level(id)).isEqualTo(1);
        // the status stays where the work is: escalation is a flag, not a state
        assertThat(get("/complaints/" + id, token(ROADS)).text("status")).isEqualTo("APPROVED");
        Integer officerNotes = jdbc.queryForObject("select count(*) from notifications where complaint_id = ? and type = 'ESCALATION' "
                + "and user_id = ?", Integer.class, id, userId(ROADS));
        assertThat(officerNotes).isEqualTo(1);
    }

    @Test
    void longOverdueComplaintReachesLevelTwoAndAdminsAreTold() {
        long id = approved("Pothole near railway crossing");
        moveDeadline(id, "60 hours", "30 hours");                           // level 2 after 24 h past the deadline
        escalations.runOnce(Instant.now());
        assertThat(level(id)).isEqualTo(2);
        assertThat(escalationRows(id)).isEqualTo(2);
        Integer adminNotes = jdbc.queryForObject("select count(*) from notifications where complaint_id = ? and type = 'ESCALATION' "
                + "and user_id = ?", Integer.class, id, userId(ADMIN));
        assertThat(adminNotes).isEqualTo(1);
    }

    @Test
    void parallelRunsOnSeveralServersStillCreateOneRowPerLevel() throws Exception {
        long id = approved("Pothole near bus depot");
        moveDeadline(id, "30 hours", "1 hour");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> runs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            runs.add(pool.submit(() -> {
                start.await();
                return escalations.runOnce(Instant.now());
            }));
        }
        start.countDown();
        for (Future<?> f : runs) f.get();
        pool.shutdown();
        assertThat(escalationRows(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications where complaint_id = ? and type = 'ESCALATION'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void warningIsSentOnceWhenDeadlineIsNear() {
        long id = approved("Pothole near college");
        // accepted 20 h ago, due in 4 h -> 83% of the time used, the warning threshold is 75%
        jdbc.update("update complaints set accepted_at = now() - interval '20 hours', sla_due_at = now() + interval '4 hours', "
                + "version = version + 1 where id = ?", id);
        escalations.runOnce(Instant.now());
        escalations.runOnce(Instant.now());
        assertThat(jdbc.queryForObject("select sla_warned_at is not null from complaints where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from notifications where complaint_id = ? and type = 'SLA_WARNING'",
                Integer.class, id)).isEqualTo(1);
        assertThat(escalationRows(id)).isZero();
    }

    @Test
    void complaintsNotUnderSlaAreNeverEscalated() {
        long id = submit("Garbage near park", "Garbage dumped near the park for a week.", LAT, LNG).id();
        awaitStatus(id, "UNDER_REVIEW");
        jdbc.update("update complaints set sla_due_at = now() - interval '5 hours' where id = ?", id);
        escalations.runOnce(Instant.now());
        assertThat(escalationRows(id)).isZero();
    }

    @Test
    void resolvedComplaintIsClosedAutomaticallyAfterTheReopenWindow() {
        long id = approved("Pothole near stadium");
        jdbc.update("update complaints set status = 'RESOLVED', resolved_at = now() - interval '8 days', version = version + 1 where id = ?", id);
        escalations.runOnce(Instant.now());
        assertThat(get("/complaints/" + id, token(ROADS)).text("status")).isEqualTo("CLOSED");
    }
}
