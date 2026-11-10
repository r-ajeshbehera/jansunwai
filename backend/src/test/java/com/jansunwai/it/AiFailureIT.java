package com.jansunwai.it;

import com.jansunwai.ai.AiProvider;
import com.jansunwai.ai.AiProviderException;
import com.jansunwai.ai.RawClassification;
import com.jansunwai.service.AiPipelineService;
import com.jansunwai.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** The AI provider is replaced by a Mockito mock that fails. Complaints must never be lost because AI is down. */
class AiFailureIT extends IntegrationTestBase {
    @MockitoBean
    AiProvider provider;

    @Autowired
    AiPipelineService pipeline;

    private void providerIs(String name) {
        when(provider.name()).thenReturn(name);
        when(provider.chatModel()).thenReturn("test-model");
        when(provider.embeddingModel()).thenReturn("test-embedding");
    }

    @Test
    void providerDownComplaintIsSavedAndFallsBackToManualReviewAfterTheRetries() {
        providerIs("down");
        when(provider.classify(any())).thenThrow(new AiProviderException(AiProviderException.Code.UNAVAILABLE, "503"));
        when(provider.embed(anyString())).thenThrow(new AiProviderException(AiProviderException.Code.UNAVAILABLE, "503"));

        var r = post("/complaints", Map.of("title", "Pothole near bank", "description", "Big pothole on the road near the bank."),
                token(CITIZEN));
        assertThat(r.status()).isEqualTo(201);                       // saved immediately, AI runs afterwards
        long id = r.id();
        awaitStatus(id, "AI_ANALYSIS_PENDING");
        long end = System.currentTimeMillis() + 15_000;
        while (jdbc.queryForObject("select count(*) from complaint_ai_analyses where complaint_id = ?", Integer.class, id) == 0
                && System.currentTimeMillis() < end) sleep(100);

        pipeline.process(id);                                        // what the retry job does
        pipeline.process(id);                                        // third and last attempt (app.ai.max-attempts = 3)
        var detail = get("/complaints/" + id, token(ADMIN)).body();
        assertThat(detail.path("status").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(detail.path("category").isNull()).isTrue();       // nothing was guessed
        assertThat(jdbc.queryForObject("select count(*) from complaint_ai_analyses where complaint_id = ? and status = 'FAILED' "
                + "and error_code = 'UNAVAILABLE'", Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from notifications where complaint_id = ? and type = 'MANUAL_REVIEW'",
                Integer.class, id)).isGreaterThanOrEqualTo(1);

        // an officer can still classify and approve by hand
        var approved = post("/complaints/" + id + "/review", Map.of("decision", "APPROVE", "category", "ROAD_DAMAGE",
                "departmentId", departmentId("PUBLIC_WORKS"), "priority", "MEDIUM"), token(ROADS));
        assertThat(approved.text("status")).isEqualTo("APPROVED");
        assertThat(approved.text("classificationSource")).isEqualTo("OFFICER_CORRECTED");
    }

    @Test
    void invalidAiOutputIsRefusedInsteadOfUsed() {
        providerIs("liar");
        when(provider.classify(any())).thenReturn(new AiProvider.ClassificationResult(
                new RawClassification("FLYING_SAUCER", "NASA", "CRITICAL", "en", "x", null, null, 7.0), 10, 5));
        when(provider.embed(anyString())).thenReturn(new float[768]);
        long id = post("/complaints", Map.of("title", "Garbage", "description", "Garbage on the road for days."), token(CITIZEN)).id();
        long end = System.currentTimeMillis() + 15_000;
        while (jdbc.queryForObject("select count(*) from complaint_ai_analyses where complaint_id = ?", Integer.class, id) == 0
                && System.currentTimeMillis() < end) sleep(100);
        assertThat(jdbc.queryForObject("select error_code from complaint_ai_analyses where complaint_id = ? order by id limit 1",
                String.class, id)).isEqualTo("INVALID_OUTPUT");
        assertThat(get("/complaints/" + id, token(ADMIN)).body().path("category").isNull()).isTrue();
    }

    @Test
    void lowConfidenceSuggestionIsKeptButNotUsedForRouting() {
        providerIs("unsure");
        when(provider.classify(any())).thenReturn(new AiProvider.ClassificationResult(
                new RawClassification("GARBAGE", "SANITATION", "LOW", "en", "Maybe garbage", null, "Inspect", 0.31), 10, 5));
        when(provider.embed(anyString())).thenReturn(new float[768]);
        long id = post("/complaints", Map.of("title", "Something smells", "description", "There is a strange smell in the lane."),
                token(CITIZEN)).id();
        var detail = awaitStatus(id, "UNDER_REVIEW");
        assertThat(detail.at("/aiAnalysis/category").asText()).isEqualTo("GARBAGE");
        assertThat(detail.path("departmentName").isNull()).isTrue();      // not routed: an officer decides
    }
}
