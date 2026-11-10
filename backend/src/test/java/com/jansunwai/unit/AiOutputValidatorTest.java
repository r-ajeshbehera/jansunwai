package com.jansunwai.unit;

import com.jansunwai.ai.AiOutputValidator;
import com.jansunwai.ai.AiProviderException;
import com.jansunwai.ai.RawClassification;
import com.jansunwai.entity.Category;
import com.jansunwai.entity.Priority;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AI output is untrusted: anything outside the allowed values must be refused, never guessed. */
class AiOutputValidatorTest {
    private final AiOutputValidator v = new AiOutputValidator();
    private final Set<String> depts = Set.of("PUBLIC_WORKS", "WATER_SUPPLY", "DRAINAGE", "SANITATION", "ELECTRICAL", "GENERAL");

    private RawClassification raw(String cat, String dept, String prio, String lang, String summary, Double conf) {
        return new RawClassification(cat, dept, prio, lang, summary, null, "Inspect", conf);
    }

    @Test
    void validOutputIsAccepted() {
        var r = v.validate(raw("road damage", "public_works", "high", "hi", "Pothole near market", 0.873), "en", depts);
        assertThat(r.category()).isEqualTo(Category.ROAD_DAMAGE);
        assertThat(r.priority()).isEqualTo(Priority.HIGH);
        assertThat(r.departmentCode()).isEqualTo("PUBLIC_WORKS");
        assertThat(r.language()).isEqualTo("hi");
        assertThat(r.confidence()).hasToString("0.87");
    }

    @Test
    void missingDepartmentFallsBackToCategoryDefault() {
        var r = v.validate(raw("WATER_SUPPLY", null, "MEDIUM", "en", "No water", 0.9), "en", depts);
        assertThat(r.departmentCode()).isEqualTo(Category.WATER_SUPPLY.defaultDepartmentCode());
    }

    @Test
    void unknownCategoryIsRejected() {
        assertThatThrownBy(() -> v.validate(raw("ALIENS", null, "HIGH", "en", "x", 0.9), "en", depts))
                .isInstanceOf(AiProviderException.class).hasMessageContaining("unknown category");
    }

    @Test
    void unknownDepartmentIsRejected() {
        assertThatThrownBy(() -> v.validate(raw("GARBAGE", "MAYOR_OFFICE", "LOW", "en", "x", 0.9), "en", depts))
                .isInstanceOf(AiProviderException.class).hasMessageContaining("unknown department");
    }

    @Test
    void confidenceOutsideZeroToOneIsRejected() {
        assertThatThrownBy(() -> v.validate(raw("GARBAGE", null, "LOW", "en", "x", 1.5), "en", depts))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> v.validate(raw("GARBAGE", null, "LOW", "en", "x", Double.NaN), "en", depts))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> v.validate(raw("GARBAGE", null, "LOW", "en", "x", null), "en", depts))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void missingSummaryOrNullResponseIsRejected() {
        assertThatThrownBy(() -> v.validate(raw("GARBAGE", null, "LOW", "en", "  ", 0.8), "en", depts))
                .isInstanceOf(AiProviderException.class);
        assertThatThrownBy(() -> v.validate(null, "en", depts)).isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).getCode()).isEqualTo(AiProviderException.Code.INVALID_OUTPUT);
    }

    @Test
    void unsupportedLanguageFallsBackAndTextIsCleaned() {
        var r = v.validate(raw("GARBAGE", null, "LOW", "fr", "Garbage\u0000 pile" + "x".repeat(600), 0.8), "or", depts);
        assertThat(r.language()).isEqualTo("or");
        assertThat(r.summaryEn()).doesNotContain("\u0000").hasSizeLessThanOrEqualTo(400);
    }
}
