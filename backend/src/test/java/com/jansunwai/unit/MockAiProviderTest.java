package com.jansunwai.unit;

import com.jansunwai.ai.AiProvider.ComplaintText;
import com.jansunwai.ai.MockAiProvider;
import com.jansunwai.support.TestProps;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {
    private final MockAiProvider ai = new MockAiProvider(TestProps.defaults());

    private String category(String title, String description) {
        return ai.classify(new ComplaintText(title, description, null)).raw().category();
    }

    @Test
    void classifiesEnglishHindiAndOdia() {
        assertThat(category("Big pothole on main road", "Deep pothole, two bikes fell yesterday")).isEqualTo("ROAD_DAMAGE");
        assertThat(category("बाज़ार के पास गड्ढा", "सड़क पर बड़ा गड्ढा है")).isEqualTo("ROAD_DAMAGE");
        assertThat(ai.classify(new ComplaintText("ପାଣି ଆସୁନାହିଁ", "ତିନି ଦିନ ହେଲା ପାଣି ଆସୁନାହିଁ", null)).raw().language()).isEqualTo("or");
    }

    @Test
    void titleWeighsMoreThanAPassingWordInTheDescription() {
        assertThat(category("Garbage pile near school gate", "Garbage is dumped next to the road every day")).isEqualTo("GARBAGE");
    }

    @Test
    void dangerWordsRaisePriority() {
        var r = ai.classify(new ComplaintText("Open manhole", "Open manhole on the road, very dangerous, a child may fall", null)).raw();
        assertThat(r.priority()).isEqualTo("HIGH");
    }

    @Test
    void unrelatedTextIsOtherWithLowConfidence() {
        var r = ai.classify(new ComplaintText("Hello", "I want to say something about my neighbour's dog barking", null)).raw();
        assertThat(r.category()).isEqualTo("OTHER");
        assertThat(r.confidence()).isLessThan(0.60);
    }

    @Test
    void embeddingIsDeterministicNormalisedAndHasConfiguredSize() {
        float[] a = ai.embed("Pothole near market");
        float[] b = ai.embed("Pothole near market");
        assertThat(a).hasSize(768).containsExactly(b);
        double norm = 0;
        for (float f : a) norm += f * f;
        assertThat(Math.sqrt(norm)).isBetween(0.99, 1.01);
    }

    @Test
    void similarComplaintsAreCloserThanUnrelatedOnes() {
        float[] pothole = ai.embed("Big pothole on the road near the market");
        float[] pothole2 = ai.embed("Pothole near market road damaged");
        float[] water = ai.embed("No water supply for three days");
        assertThat(cos(pothole, pothole2)).isGreaterThan(cos(pothole, water));
    }

    private static double cos(float[] a, float[] b) {
        double d = 0;
        for (int i = 0; i < a.length; i++) d += a[i] * b[i];
        return d;
    }
}
