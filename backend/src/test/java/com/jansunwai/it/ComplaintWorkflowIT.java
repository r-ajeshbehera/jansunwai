package com.jansunwai.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.jansunwai.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComplaintWorkflowIT extends IntegrationTestBase {

    @Test
    void fullLifecycleFromSubmissionToCitizenConfirmation() {
        var created = submit("Drain overflowing near bus stop", "The drain near the bus stop is overflowing with sewage for two days.", LAT, LNG);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.text("trackingId")).matches("JS-\\d{4}-\\d{6}");
        assertThat(created.text("wardName")).isEqualTo("Saheed Nagar (DEMO)");          // detected by PostGIS
        long id = created.id();

        JsonNode reviewed = awaitStatus(id, "UNDER_REVIEW");
        assertThat(reviewed.at("/aiAnalysis/category").asText()).isEqualTo("DRAINAGE");
        assertThat(reviewed.path("classificationSource").asText()).isEqualTo("AI_SUGGESTED");

        String officer = token(DRAINAGE), field = token(DRAINAGE_FIELD), citizen = token(CITIZEN);
        var approved = post("/complaints/" + id + "/review", Map.of("decision", "APPROVE", "category", "DRAINAGE",
                "departmentId", departmentId("DRAINAGE"), "priority", "HIGH"), officer);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.text("status")).isEqualTo("APPROVED");
        assertThat(approved.text("classificationSource")).isEqualTo("OFFICER_CONFIRMED");
        assertThat(approved.body().path("slaDueAt").isNull()).isFalse();

        assertThat(post("/complaints/" + id + "/assign", Map.of("fieldOfficerId", userId(DRAINAGE_FIELD)), officer).text("status"))
                .isEqualTo("ASSIGNED");
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "IN_PROGRESS"), field).text("status")).isEqualTo("IN_PROGRESS");
        assertThat(upload("/complaints/" + id + "/attachments", "RESOLUTION", "done.png", "image/png", PNG, field).status()).isEqualTo(201);
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "RESOLUTION_SUBMITTED", "note", "Drain cleaned"), field)
                .text("status")).isEqualTo("RESOLUTION_SUBMITTED");
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "RESOLVED"), officer).text("status")).isEqualTo("RESOLVED");
        var closed = patch("/complaints/" + id + "/status", Map.of("status", "CLOSED"), citizen);
        assertThat(closed.text("status")).isEqualTo("CLOSED");

        List<String> steps = new ArrayList<>();
        get("/complaints/" + id, officer).body().path("timeline").forEach(t -> steps.add(t.path("toStatus").asText()));
        assertThat(steps).containsSubsequence("SUBMITTED", "AI_ANALYSIS_PENDING", "UNDER_REVIEW", "APPROVED", "ASSIGNED",
                "IN_PROGRESS", "RESOLUTION_SUBMITTED", "RESOLVED", "CLOSED");
        assertThat(closed.body().path("allowedActions")).isEmpty();                  // final state: nothing left to do
        // the citizen's timeline leaves out internal steps (AI processing, field team's report)
        List<String> citizenSteps = new ArrayList<>();
        closed.body().path("timeline").forEach(t -> citizenSteps.add(t.path("toStatus").asText()));
        assertThat(citizenSteps).doesNotContain("AI_ANALYSIS_PENDING", "RESOLUTION_SUBMITTED").contains("SUBMITTED", "CLOSED");

        Integer notes = jdbc.queryForObject("select count(*) from notifications where complaint_id = ? and user_id = ?",
                Integer.class, id, userId(CITIZEN));
        assertThat(notes).isEqualTo(3);                                                  // approved, work started, resolved
    }

    @Test
    void citizenCannotApproveOrAssign() {
        long id = submit("Streetlight not working", "The streetlight near my house is not working for a week.", LAT, LNG).id();
        awaitStatus(id, "UNDER_REVIEW");
        String citizen = token(CITIZEN);
        assertThat(post("/complaints/" + id + "/review", Map.of("decision", "APPROVE"), citizen).status()).isEqualTo(403);
        assertThat(post("/complaints/" + id + "/assign", Map.of("fieldOfficerId", 1), citizen).status()).isEqualTo(403);
    }

    @Test
    void invalidTransitionsAreRejected() {
        long id = approvedComplaint("Pothole near temple", "Deep pothole on the road near the temple, bikes fall.", ROADS,
                "PUBLIC_WORKS", "ROAD_DAMAGE", "HIGH");
        // APPROVED -> RESOLVED skips assignment and work
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "RESOLVED"), token(ROADS)).status()).isEqualTo(409);
        // statuses owned by other endpoints cannot be forced
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "ASSIGNED"), token(ROADS)).status()).isEqualTo(422);
        // a second review of an already approved complaint
        assertThat(post("/complaints/" + id + "/review", Map.of("decision", "REJECT", "reason", "x"), token(ROADS)).status())
                .isEqualTo(409);
    }

    @Test
    void resolutionNeedsAPhotoAndCanBeSentBackAndReopened() {
        long id = approvedComplaint("Broken road edge", "The road edge is broken near the school, road damage everywhere.", ROADS,
                "PUBLIC_WORKS", "ROAD_DAMAGE", "MEDIUM");
        String officer = token(ROADS), field = token(ROADS_FIELD), citizen = token(CITIZEN);
        post("/complaints/" + id + "/assign", Map.of("fieldOfficerId", userId(ROADS_FIELD)), officer);
        patch("/complaints/" + id + "/status", Map.of("status", "IN_PROGRESS"), field);

        var noPhoto = patch("/complaints/" + id + "/status", Map.of("status", "RESOLUTION_SUBMITTED", "note", "Fixed"), field);
        assertThat(noPhoto.status()).isEqualTo(422);
        assertThat(noPhoto.text("code")).isEqualTo("EVIDENCE_REQUIRED");

        upload("/complaints/" + id + "/attachments", "RESOLUTION", "fixed.png", "image/png", PNG, field);
        patch("/complaints/" + id + "/status", Map.of("status", "RESOLUTION_SUBMITTED", "note", "Fixed"), field);
        // department is not satisfied: send back (needs a note)
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "IN_PROGRESS"), officer).status()).isEqualTo(422);
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "IN_PROGRESS", "note", "Edge still broken"), officer)
                .text("status")).isEqualTo("IN_PROGRESS");
        patch("/complaints/" + id + "/status", Map.of("status", "RESOLUTION_SUBMITTED", "note", "Edge rebuilt"), field);
        patch("/complaints/" + id + "/status", Map.of("status", "RESOLVED"), officer);

        // the citizen is not happy: reopen needs a reason, then it goes back to the department
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "REOPENED"), citizen).status()).isEqualTo(422);
        var reopened = patch("/complaints/" + id + "/status", Map.of("status", "REOPENED", "note", "Broken again after rain"), citizen);
        assertThat(reopened.text("status")).isEqualTo("REOPENED");
        var reassigned = post("/complaints/" + id + "/assign", Map.of("fieldOfficerId", userId(ROADS_FIELD2)), officer);
        assertThat(reassigned.text("status")).isEqualTo("ASSIGNED");
        assertThat(reassigned.body().at("/assignment/fieldOfficerId").asLong()).isEqualTo(userId(ROADS_FIELD2));
        Integer active = jdbc.queryForObject("select count(*) from complaint_assignments where complaint_id = ? and active", Integer.class, id);
        assertThat(active).isEqualTo(1);
    }

    @Test
    void rejectionNeedsAReasonAndIsFinal() {
        long id = submit("Garbage not collected", "Garbage is not collected from our lane for many days.", LAT, LNG).id();
        awaitStatus(id, "UNDER_REVIEW");
        String officer = token(ADMIN);
        assertThat(post("/complaints/" + id + "/review", Map.of("decision", "REJECT"), officer).text("code")).isEqualTo("REASON_REQUIRED");
        var rejected = post("/complaints/" + id + "/review", Map.of("decision", "REJECT", "reason", "Private property, not municipal"), officer);
        assertThat(rejected.text("status")).isEqualTo("REJECTED");
        assertThat(patch("/complaints/" + id + "/status", Map.of("status", "CLOSED"), token(CITIZEN)).status()).isEqualTo(409);
        // the citizen is told why; the public page only says "Not accepted" (reasons may contain personal details)
        assertThat(jdbc.queryForObject("select message from notifications where complaint_id = ? and type = 'REJECTED'",
                String.class, id)).contains("Private property");
        String pub = get("/public/track/" + rejected.text("trackingId"), null).body().toString();
        assertThat(pub).contains("Not accepted").doesNotContain("Private property");
    }

    @Test
    void officerCanOnlyAcceptForOwnDepartmentButCanReroute() {
        long id = submit("No water supply", "No water supply in our street for three days.", LAT, LNG).id();
        awaitStatus(id, "UNDER_REVIEW");
        var wrong = post("/complaints/" + id + "/review", Map.of("decision", "APPROVE", "category", "WATER_SUPPLY",
                "departmentId", departmentId("DRAINAGE"), "priority", "HIGH"), token(WATER));
        assertThat(wrong.status()).isEqualTo(422);
        assertThat(wrong.text("code")).isEqualTo("WRONG_DEPARTMENT");
        var rerouted = post("/complaints/" + id + "/review", Map.of("decision", "REROUTE", "departmentId", departmentId("DRAINAGE")),
                token(WATER));
        assertThat(rerouted.status()).isEqualTo(200);
        assertThat(rerouted.text("status")).isEqualTo("UNDER_REVIEW");
        assertThat(rerouted.text("departmentName")).isEqualTo("Drainage & Sewerage");
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() {
        String citizen = token(CITIZEN);
        assertThat(post("/complaints", Map.of("title", "", "description", "short"), citizen).status()).isEqualTo(400);
        assertThat(post("/complaints", Map.of("title", "Valid title", "description", "A valid description here",
                "latitude", 123.0, "longitude", 85.0), citizen).status()).isEqualTo(400);
        assertThat(post("/complaints", Map.of("title", "Valid title", "description", "A valid description here",
                "language", "fr"), citizen).status()).isEqualTo(400);
        assertThat(get("/complaints/999999", citizen).status()).isEqualTo(404);
    }

    @Test
    void complaintWithoutLocationIsAccepted() {
        var r = submit("Garbage pile near school", "Garbage pile near the school gate, smell is bad.", null, null);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().path("wardName").isNull()).isTrue();
        assertThat(awaitStatus(r.id(), "UNDER_REVIEW").at("/aiAnalysis/category").asText()).isEqualTo("GARBAGE");
    }
}
