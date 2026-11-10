package com.jansunwai.it;

import com.jansunwai.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoDashboardIT extends IntegrationTestBase {

    @Test
    void wardIsDetectedFromTheGpsPoint() {
        assertThat(submit("Pothole in Patia", "Pothole on the road in Patia.", 20.3500, 85.8200).text("wardName")).isEqualTo("Patia (DEMO)");
        assertThat(submit("Pothole outside city", "Pothole on the highway outside the city.", 20.5000, 85.9000).body()
                .path("wardName").isNull()).isTrue();
    }

    @Test
    void wardShapesArePublicGeoJson() {
        var r = get("/wards/shapes", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().size()).isGreaterThanOrEqualTo(4);
        assertThat(r.body().get(0).toString()).contains("Polygon");
    }

    @Test
    void nearbySummaryCountsOpenComplaintsAroundAPoint() {
        var r = get("/geo/nearby-summary?lat=20.2901&lng=85.8452&radiusMeters=300", token(CITIZEN));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().toString()).contains("ROAD_DAMAGE");     // the demo market pothole group is there
    }

    @Test
    void dashboardNumbersComeFromTheDatabase() {
        var admin = get("/dashboard/summary", token(ADMIN)).body();
        long total = jdbc.queryForObject("select count(*) from complaints", Long.class);
        assertThat(admin.at("/totals/total").asLong()).isLessThanOrEqualTo(total).isPositive();
        assertThat(admin.path("byCategory").size()).isPositive();
        var roads = get("/dashboard/summary", token(ROADS)).body();
        assertThat(roads.path("scope").asText()).containsIgnoringCase("Public Works");
        assertThat(roads.at("/totals/total").asLong()).isLessThan(admin.at("/totals/total").asLong());
        assertThat(get("/geo/heatmap", token(ADMIN)).body().size()).isPositive();
        assertThat(get("/geo/hotspots", token(ADMIN)).status()).isEqualTo(200);
    }

    @Test
    void weeklyWardSummaryOnlyUsesComputedFacts() {
        var r = post("/dashboard/weekly-summary/generate?wardId=1", null, token(ADMIN));
        assertThat(r.status()).isEqualTo(200);
        var s = r.body().get(0);
        String narrative = s.path("narrative").asText();
        assertThat(narrative).contains(s.at("/facts/wardName").asText())
                .contains(s.at("/facts/received").asText() + " complaint");
        assertThat(post("/dashboard/weekly-summary/generate?wardId=1", null, token(ROADS)).status()).isEqualTo(403);
    }

    @Test
    void aiUsageIsTracked() {
        var r = get("/ai/usage", token(ADMIN));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().toString()).contains("mock");
    }
}
