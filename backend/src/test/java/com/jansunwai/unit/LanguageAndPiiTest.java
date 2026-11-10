package com.jansunwai.unit;

import com.jansunwai.ai.LanguageDetector;
import com.jansunwai.ai.PiiRedactor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageAndPiiTest {

    @ParameterizedTest(name = "{1} <- {0}")
    @CsvSource(delimiter = '|', value = {
            "Big pothole near the market|en",
            "बाज़ार के पास बड़ा गड्ढा है|hi",
            "ଆମ ଗାଁରେ ପାଣି ଆସୁନାହିଁ|or",
            "Road पर गड्ढा है, please fix|hi",
            "Sadak par gadda hai|en"})
    void detectsLanguageByScript(String text, String expected) {
        assertThat(LanguageDetector.detect(text)).isEqualTo(expected);
    }

    @Test
    void nullOrEmptyDefaultsToEnglish() {
        assertThat(LanguageDetector.detect(null)).isEqualTo("en");
        assertThat(LanguageDetector.detect("")).isEqualTo("en");
    }

    @Test
    void redactsEmailPhoneAndLongNumbers() {
        String r = PiiRedactor.redact("Call me at 9876543210 or +91 9123456789, mail ravi.k@example.com, Aadhaar 1234 5678 9012");
        assertThat(r).doesNotContain("9876543210", "9123456789", "ravi.k@example.com", "1234 5678 9012")
                .contains("[phone]", "[email]", "[number]");
    }

    @Test
    void keepsNormalTextAndShortNumbers() {
        String t = "Pothole of 2 feet near house no. 45, ward 12";
        assertThat(PiiRedactor.redact(t)).isEqualTo(t);
        assertThat(PiiRedactor.redact(null)).isNull();
    }
}
