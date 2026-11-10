package com.jansunwai.unit;

import com.jansunwai.entity.Role;
import com.jansunwai.security.AppUserPrincipal;
import com.jansunwai.security.JwtService;
import com.jansunwai.service.SlaService;
import com.jansunwai.support.TestProps;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlaAndTokenTest {

    @Test
    void dueAndWarningTimes() {
        Instant accepted = Instant.parse("2026-01-01T00:00:00Z");
        Instant due = SlaService.dueAt(accepted, 24);
        assertThat(due).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(SlaService.warningAt(accepted, due, 75)).isEqualTo(accepted.plus(Duration.ofHours(18)));
    }

    @Test
    void jwtRoundTripAndTamperDetection() {
        JwtService jwt = new JwtService(TestProps.defaults());
        AppUserPrincipal p = new AppUserPrincipal(7L, "c@x.demo", "C", "hash", Role.CITIZEN, null, true);
        var issued = jwt.issue(p);
        var parsed = jwt.parse(issued.token()).orElseThrow();
        assertThat(parsed.userId()).isEqualTo(7L);
        assertThat(parsed.jti()).isNotBlank();
        String tampered = issued.token().substring(0, issued.token().length() - 3) + "abc";
        assertThat(jwt.parse(tampered)).isEmpty();
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
    }
}
