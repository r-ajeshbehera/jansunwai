package com.jansunwai.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.jansunwai.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Semantic duplicates (pgvector + PostGIS) are only SUGGESTED; linking needs an officer's approval.
 * Each test uses its own location (more than 500 m apart) so tests cannot match each other's complaints.
 */
class DuplicateDetectionIT extends IntegrationTestBase {
    private static final String MASTER_TITLE = "Big pothole near the main market";
    private static final String MASTER_TEXT = "There is a big pothole near the main market. Two-wheelers are falling at night.";

    private long submitAt(String title, String text, double lat, double lng) {
        lat = Math.round(lat * 1e6) / 1e6;                       // the API accepts at most 6 decimals
        lng = Math.round(lng * 1e6) / 1e6;
        long id = post("/complaints", Map.of("title", title, "description", text, "latitude", lat, "longitude", lng),
                token(CITIZEN)).id();
        awaitStatus(id, "UNDER_REVIEW");
        return id;
    }

    private JsonNode candidateFor(long complaintId) {
        JsonNode pending = get("/ai/duplicates/pending", token(ROADS)).body();
        return StreamSupport.stream(pending.spliterator(), false)
                .filter(c -> c.at("/complaint/id").asLong() == complaintId).findFirst().orElse(null);
    }

    @Test
    void hindiComplaintAboutTheSamePotholeIsSuggestedAndLinkedOnlyAfterApproval() {
        double lat = 20.2820, lng = 85.8530;
        long master = submitAt(MASTER_TITLE, MASTER_TEXT, lat, lng);
        long hindi = submitAt("बाज़ार के पास गड्ढा", "मुख्य बाज़ार के पास सड़क पर बड़ा गड्ढा है, रात में दुर्घटना हो सकती है।",
                lat + 0.0003, lng - 0.0003);                                           // about 45 m away

        JsonNode cand = candidateFor(hindi);
        assertThat(cand).as("duplicate suggestion for the Hindi complaint").isNotNull();
        assertThat(cand.at("/candidate/id").asLong()).isEqualTo(master);
        assertThat(cand.path("distanceMeters").asInt()).isBetween(1, 100);
        assertThat(cand.path("totalScore").asDouble()).isGreaterThanOrEqualTo(0.60);

        // a suggestion changes nothing by itself
        assertThat(get("/complaints/" + hindi, token(ROADS)).body().path("duplicate").isNull()).isTrue();

        var approved = post("/ai/duplicates/candidates/" + cand.path("id").asLong() + "/approve", null, token(ROADS));
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.text("status")).isEqualTo("APPROVED");
        JsonNode child = get("/complaints/" + hindi, token(ROADS)).body();
        assertThat(child.at("/duplicate/masterTrackingId").asText())
                .isEqualTo(get("/complaints/" + master, token(ROADS)).body().path("trackingId").asText());
        assertThat(child.path("allowedActions").toString()).doesNotContain("\"ASSIGN\"");      // the master is worked on

        // approving twice is refused
        assertThat(post("/ai/duplicates/candidates/" + cand.path("id").asLong() + "/approve", null, token(ROADS)).status()).isEqualTo(409);

        // working the master resolves the linked complaint too, and the Hindi citizen keeps their own tracking ID
        post("/complaints/" + master + "/review", Map.of("decision", "APPROVE", "category", "ROAD_DAMAGE",
                "departmentId", departmentId("PUBLIC_WORKS"), "priority", "HIGH"), token(ROADS));
        assertThat(get("/complaints/" + hindi, token(ROADS)).text("status")).isEqualTo("APPROVED");
        post("/complaints/" + master + "/assign", Map.of("fieldOfficerId", userId(ROADS_FIELD)), token(ROADS));
        patch("/complaints/" + master + "/status", Map.of("status", "IN_PROGRESS"), token(ROADS_FIELD));
        upload("/complaints/" + master + "/attachments", "RESOLUTION", "fixed.png", "image/png", PNG, token(ROADS_FIELD));
        patch("/complaints/" + master + "/status", Map.of("status", "RESOLUTION_SUBMITTED", "note", "Filled"), token(ROADS_FIELD));
        assertThat(patch("/complaints/" + master + "/status", Map.of("status", "RESOLVED"), token(ROADS)).text("status")).isEqualTo("RESOLVED");
        assertThat(get("/complaints/" + hindi, token(ROADS)).text("status")).isEqualTo("RESOLVED");
    }

    @Test
    void rejectedSuggestionLeavesBothComplaintsIndependent() {
        double lat = 20.2995, lng = 85.8370;
        long master = submitAt(MASTER_TITLE, MASTER_TEXT, lat, lng);
        long similar = submitAt("Road damaged near market entrance",
                "Road damaged near the market entrance, a large hole beside the main market road.", lat + 0.0002, lng + 0.0002);
        JsonNode cand = candidateFor(similar);
        assertThat(cand).isNotNull();
        assertThat(cand.at("/candidate/id").asLong()).isEqualTo(master);
        assertThat(post("/ai/duplicates/candidates/" + cand.path("id").asLong() + "/reject", null, token(ROADS)).text("status"))
                .isEqualTo("REJECTED");
        assertThat(get("/complaints/" + similar, token(ROADS)).body().path("duplicate").isNull()).isTrue();
        assertThat(candidateFor(similar)).isNull();
    }

    @Test
    void samePlaceButDifferentProblemOrSameProblemFarAwayIsNotADuplicate() {
        double lat = 20.3600, lng = 85.8120;
        submitAt(MASTER_TITLE, MASTER_TEXT, lat, lng);
        long water = submitAt("No water supply", "No drinking water supply in our colony for three days.", lat, lng);
        long far = submitAt(MASTER_TITLE, MASTER_TEXT, lat - 0.0100, lng);           // about 1.1 km south
        Integer n = jdbc.queryForObject("select count(*) from duplicate_candidates where complaint_id in (?, ?) and candidate_id in "
                + "(select id from complaints where latitude between ? and ?)", Integer.class, water, far, lat - 0.02, lat + 0.02);
        assertThat(n).isZero();
    }

    @Test
    void citizenCannotSeeOrDecideSuggestions() {
        assertThat(get("/ai/duplicates/pending", token(CITIZEN)).status()).isEqualTo(403);
        assertThat(post("/ai/duplicates/candidates/1/approve", null, token(CITIZEN)).status()).isEqualTo(403);
    }
}
