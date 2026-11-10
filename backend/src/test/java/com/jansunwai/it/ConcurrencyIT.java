package com.jansunwai.it;

import com.jansunwai.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Real parallel HTTP requests against the real database. */
class ConcurrencyIT extends IntegrationTestBase {

    private <T> List<T> race(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> t : tasks) futures.add(pool.submit(() -> { go.await(); return t.call(); }));
        go.countDown();
        List<T> out = new ArrayList<>();
        for (Future<T> f : futures) out.add(f.get());
        pool.shutdown();
        return out;
    }

    @Test
    void twoOfficersAssigningAtTheSameTimeProduceExactlyOneAssignment() throws Exception {
        for (int round = 0; round < 3; round++) {                 // repeat: a race that passes once may be luck
            long id = approvedComplaint("Pothole near flyover " + round, "Big pothole on the road under the flyover.", ROADS,
                    "PUBLIC_WORKS", "ROAD_DAMAGE", "MEDIUM");
            String officer = token(ROADS), admin = token(ADMIN);
            List<Res> results = race(List.of(
                    () -> post("/complaints/" + id + "/assign", Map.of("fieldOfficerId", userId(ROADS_FIELD)), officer),
                    () -> post("/complaints/" + id + "/assign", Map.of("fieldOfficerId", userId(ROADS_FIELD2)), admin)));
            long ok = results.stream().filter(r -> r.status() == 200).count();
            assertThat(ok).as("exactly one assignment wins, statuses " + results.stream().map(Res::status).toList()).isEqualTo(1);
            assertThat(results).filteredOn(r -> r.status() != 200).allMatch(r -> r.status() == 409);
            assertThat(jdbc.queryForObject("select count(*) from complaint_assignments where complaint_id = ? and active",
                    Integer.class, id)).isEqualTo(1);
        }
    }

    @Test
    void simultaneousSubmissionsGetUniqueTrackingIds() throws Exception {
        List<Callable<Res>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int n = i;
            tasks.add(() -> post("/complaints", Map.of("title", "Streetlight off " + n,
                    "description", "Streetlight number " + n + " is not working at night."), token(CITIZEN2)));
        }
        List<Res> results = race(tasks);
        Set<String> ids = new HashSet<>();
        for (Res r : results) {
            assertThat(r.status()).isEqualTo(201);
            ids.add(r.text("trackingId"));
        }
        assertThat(ids).hasSize(20);
    }

    @Test
    void doubleClickOnStartWorkChangesTheStatusOnce() throws Exception {
        long id = approvedComplaint("Pothole near bridge", "Pothole on the road near the bridge.", ROADS, "PUBLIC_WORKS",
                "ROAD_DAMAGE", "LOW");
        post("/complaints/" + id + "/assign", Map.of("fieldOfficerId", userId(ROADS_FIELD)), token(ROADS));
        String field = token(ROADS_FIELD);
        List<Res> results = race(List.of(
                () -> patch("/complaints/" + id + "/status", Map.of("status", "IN_PROGRESS"), field),
                () -> patch("/complaints/" + id + "/status", Map.of("status", "IN_PROGRESS"), field)));
        assertThat(results.stream().filter(r -> r.status() == 200).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from complaint_status_history where complaint_id = ? and to_status = 'IN_PROGRESS'",
                Integer.class, id)).isEqualTo(1);
    }
}
