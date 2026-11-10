package com.jansunwai.ai;

import com.jansunwai.entity.Category;
import com.jansunwai.entity.Priority;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * AI output is untrusted input. This turns a RawClassification into a safe AiClassification or throws
 * INVALID_OUTPUT. Unknown categories/priorities are errors, not guesses; text fields are length-limited.
 */
@Component
public class AiOutputValidator {
    private static final Set<String> LANGUAGES = Set.of("en", "hi", "or");

    public AiClassification validate(RawClassification raw, String fallbackLanguage, Set<String> knownDepartments) {
        if (raw == null) throw invalid("empty response");
        Category category = parseEnum(Category.class, raw.category(), "category");
        Priority priority = parseEnum(Priority.class, raw.priority(), "priority");

        String dept = raw.department() == null ? category.defaultDepartmentCode() : raw.department().trim().toUpperCase(Locale.ROOT);
        if (!knownDepartments.contains(dept)) throw invalid("unknown department '" + dept + "'");

        Double conf = raw.confidence();
        if (conf == null || conf.isNaN() || conf < 0 || conf > 1) throw invalid("confidence must be between 0 and 1");

        String lang = raw.language() == null ? fallbackLanguage : raw.language().trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(lang)) lang = fallbackLanguage;

        String summary = clean(raw.summary(), 400);
        if (summary == null || summary.isBlank()) throw invalid("summary is missing");

        return new AiClassification(category, dept, priority, lang, summary, clean(raw.translation(), 4000),
                clean(raw.suggestedActions(), 600), BigDecimal.valueOf(conf).setScale(2, RoundingMode.HALF_UP));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null) throw invalid(field + " is missing");
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw invalid("unknown " + field + " '" + value + "'");
        }
    }

    /** Trim, strip control characters, limit length. */
    static String clean(String s, int max) {
        if (s == null) return null;
        String t = s.replaceAll("[\\p{Cntrl}&&[^\n]]", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static AiProviderException invalid(String why) {
        return new AiProviderException(AiProviderException.Code.INVALID_OUTPUT, "Invalid AI output: " + why);
    }
}
