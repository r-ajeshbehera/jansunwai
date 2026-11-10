package com.jansunwai.it;

import com.jansunwai.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPrivacyIT extends IntegrationTestBase {

    @Test
    void protectedEndpointsNeedAValidToken() {
        assertThat(get("/complaints", null).status()).isEqualTo(401);
        assertThat(get("/complaints", "garbage.token.value").status()).isEqualTo(401);
        assertThat(get("/auth/me", null).status()).isEqualTo(401);
    }

    @Test
    void wrongPasswordIsRejectedWithoutSayingWhichPartWasWrong() {
        var r = post("/auth/login", Map.of("email", CITIZEN, "password", "wrong-password"), null);
        var unknown = post("/auth/login", Map.of("email", "nobody@jansunwai.demo", "password", "wrong-password"), null);
        assertThat(r.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(r.text("message")).isEqualTo(unknown.text("message"));
    }

    @Test
    void registrationAlwaysCreatesACitizenAndRejectsDuplicates() {
        String email = "new.citizen." + System.nanoTime() + "@example.com";
        var body = Map.of("fullName", "New Citizen", "email", email, "phone", "9876501234", "password", "Strong@123",
                "role", "ADMIN");                                                    // must be ignored
        assertThat(post("/auth/register", body, null).status()).isEqualTo(201);
        var login = post("/auth/login", Map.of("email", email, "password", "Strong@123"), null);
        assertThat(login.body().at("/user/role").asText()).isEqualTo("CITIZEN");
        var again = post("/auth/register", body, null);
        assertThat(again.status()).isEqualTo(422);
        assertThat(again.text("code")).isEqualTo("EMAIL_TAKEN");
        assertThat(post("/auth/register", Map.of("fullName", "X", "email", "weak" + System.nanoTime() + "@example.com",
                "phone", "9876501234", "password", "short"), null).status()).isEqualTo(400);
        assertThat(post("/auth/register", Map.of("fullName", "X", "email", "badphone" + System.nanoTime() + "@example.com",
                "phone", "12345", "password", "Strong@123"), null).status()).isEqualTo(400);
    }

    @Test
    void logoutRevokesTheToken() {
        String t = post("/auth/login", Map.of("email", CITIZEN2, "password", PASSWORD), null).text("accessToken");
        assertThat(get("/auth/me", t).status()).isEqualTo(200);
        assertThat(post("/auth/logout", null, t).status()).isIn(200, 204);
        assertThat(get("/auth/me", t).status()).isEqualTo(401);
    }

    @Test
    void citizensOnlySeeTheirOwnComplaintsAndStaffOnlyTheirScope() {
        long id = submit("Streetlight broken", "Streetlight pole near the park is broken and dark at night.", LAT, LNG).id();
        awaitStatus(id, "UNDER_REVIEW");
        assertThat(get("/complaints/" + id, token(CITIZEN)).status()).isEqualTo(200);
        assertThat(get("/complaints/" + id, token(CITIZEN2)).status()).isEqualTo(404);         // no existence leak
        assertThat(get("/complaints/" + id, token(ROADS_FIELD)).status()).isEqualTo(404);      // not assigned to them
        get("/complaints?size=100", token(CITIZEN2)).body().path("content")
                .forEach(c -> assertThat(c.path("id").asLong()).isNotEqualTo(id));
    }

    @Test
    void citizenDetailHidesInternalDataButStaffSeeIt() {
        long id = submit("Sewage overflow", "Sewage overflowing from the manhole onto the road.", LAT, LNG).id();
        awaitStatus(id, "UNDER_REVIEW");
        var own = get("/complaints/" + id, token(CITIZEN)).body();
        var staff = get("/complaints/" + id, token(DRAINAGE)).body();
        assertThat(own.path("citizenName").isNull()).isTrue();
        assertThat(staff.path("citizenName").asText()).isNotBlank();
        assertThat(own.path("latitude").isNull()).isFalse();           // the owner sees their own location
    }

    @Test
    void publicTrackingShowsOnlyNonPersonalFields() {
        var c = submit("Water leakage", "Water pipe leaking on the street, call me on 9876543210.", LAT, LNG);
        awaitStatus(c.id(), "UNDER_REVIEW");
        var r = get("/public/track/" + c.text("trackingId"), null);
        assertThat(r.status()).isEqualTo(200);
        String json = r.body().toString();
        assertThat(json).doesNotContain("9876543210", "latitude", "longitude", "description", "citizen", "Water pipe leaking");
        assertThat(get("/public/track/JS-2000-999999", null).status()).isEqualTo(404);
    }

    @Test
    void adminEndpointsAreAdminOnly() {
        assertThat(get("/users", token(CITIZEN)).status()).isEqualTo(403);
        assertThat(get("/users", token(ROADS)).status()).isEqualTo(403);
        assertThat(get("/audit-logs", token(ROADS)).status()).isEqualTo(403);
        assertThat(get("/users", token(ADMIN)).status()).isEqualTo(200);
        assertThat(get("/dashboard/summary", token(CITIZEN)).status()).isEqualTo(403);
    }

    @Test
    void uploadsAreCheckedByContentNotByName() {
        long id = submit("Road hole", "Hole in the road near the bank, road damage.", LAT, LNG).id();
        String citizen = token(CITIZEN);
        var fake = upload("/complaints/" + id + "/attachments", "COMPLAINT", "photo.png", "image/png",
                "MZ this is really an exe".getBytes(), citizen);
        assertThat(fake.status()).isIn(415, 422);
        var real = upload("/complaints/" + id + "/attachments", "COMPLAINT", "photo.png", "image/png", PNG, citizen);
        assertThat(real.status()).isEqualTo(201);
        // the kind is decided by the server from the role: a citizen cannot pass a photo off as RESOLUTION evidence
        long attId = upload("/complaints/" + id + "/attachments", "RESOLUTION", "x.png", "image/png", PNG, citizen).id();
        assertThat(jdbc.queryForObject("select kind from complaint_attachments where id = ?", String.class, attId)).isEqualTo("EVIDENCE");
        // someone else's complaint
        assertThat(upload("/complaints/" + id + "/attachments", "COMPLAINT", "x.png", "image/png", PNG, token(CITIZEN2)).status())
                .isEqualTo(404);
    }

    @Test
    void searchTextIsAParameterNotSql() {
        var r = get("/complaints?q=%27%20OR%201%3D1%20--", token(ADMIN));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().path("totalElements").asLong()).isZero();
    }
}
