package com.jansunwai.ai;

import com.jansunwai.config.AppProperties;
import com.jansunwai.entity.Category;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MOCK AI PROVIDER — clearly labelled, offline, deterministic. Used by default and in all tests.
 *
 * It is NOT a language model. It uses multilingual keyword dictionaries (English, Hindi, Odia) for classification
 * and a hashing "bag of words + concepts" vector for similarity. It cannot translate, so translationEn stays empty
 * and the summary says so. Switch to app.ai.provider=spring-ai for real LLM output.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {
    public static final String NAME = "mock";

    /** concept -> words in en / hi / or. Concepts are shared across languages, which makes cross-lingual matching work. */
    static final Map<String, List<String>> CONCEPTS = new LinkedHashMap<>();
    static {
        CONCEPTS.put("ROAD", List.of("pothole", "potholes", "road", "roads", "hole", "holes", "dug", "crack", "tar",
                "गड्ढा", "गड्ढे", "सड़क", "रोड", "ଗାତ", "ରାସ୍ତା", "ସଡ଼କ"));
        CONCEPTS.put("WATER", List.of("water", "tap", "pipeline", "pressure", "supply", "drinking", "पानी", "नल", "जल",
                "ପାଣି", "ଜଳ", "ଟ୍ୟାପ"));
        CONCEPTS.put("DRAIN", List.of("drain", "drainage", "sewage", "sewer", "manhole", "overflowing", "overflow",
                "waterlogging", "waterlogged", "stagnates", "नाली", "सीवर", "नाला", "ନାଳ", "ନର୍ଦ୍ଦମା", "ମଇଳା"));
        CONCEPTS.put("GARBAGE", List.of("garbage", "waste", "trash", "dump", "dumping", "dumped", "litter", "plastic",
                "कचरा", "कूड़ा", "ଅଳିଆ", "ଆବର୍ଜନା"));
        CONCEPTS.put("LIGHT", List.of("streetlight", "streetlights", "light", "lights", "lamp", "pole", "bulb", "dark",
                "बत्ती", "लाइट", "ବତୀ", "ଆଲୁଅ"));
        CONCEPTS.put("TOILET", List.of("toilet", "toilets", "urinal", "sanitation", "शौचालय", "ଶୌଚାଳୟ"));
        CONCEPTS.put("MARKET", List.of("market", "bazaar", "बाज़ार", "बाजार", "ବଜାର"));
        CONCEPTS.put("SCHOOL", List.of("school", "स्कूल", "विद्यालय", "ବିଦ୍ୟାଳୟ", "ସ୍କୁଲ"));
        CONCEPTS.put("TEMPLE", List.of("temple", "मंदिर", "ମନ୍ଦିର"));
        CONCEPTS.put("BUSSTOP", List.of("bus", "stop", "बस", "ବସ"));
    }

    static final Map<String, Category> CONCEPT_CATEGORY = Map.of(
            "ROAD", Category.ROAD_DAMAGE, "WATER", Category.WATER_SUPPLY, "DRAIN", Category.DRAINAGE,
            "GARBAGE", Category.GARBAGE, "LIGHT", Category.STREETLIGHT, "TOILET", Category.SANITATION);

    private static final List<String> URGENT = List.of("danger", "dangerous", "accident", "accidents", "fall", "falling",
            "children", "child", "elderly", "injured", "collapse", "flood", "fire", "urgent", "sewage", "open manhole",
            "suffering", "may fall", "दुर्घटना", "खतरा", "बच्चे", "ବିପଦ", "ଦୁର୍ଘଟଣା", "ଶୀଘ୍ର");
    private static final List<String> MINOR = List.of("suggestion", "request", "please install", "bench", "benches",
            "beautif", "सुझाव", "ପରାମର୍ଶ");
    private static final Set<String> STOPWORDS = Set.of("the", "a", "an", "is", "are", "was", "of", "in", "on", "at", "to",
            "for", "and", "or", "it", "this", "that", "there", "our", "we", "near", "since", "from", "with", "not", "has",
            "have", "been", "be", "very", "please", "है", "के", "की", "का", "में", "पर", "और", "ଆମ", "ହେଲା");

    private final int dimensions;

    public MockAiProvider(AppProperties props) {
        this.dimensions = props.ai().embeddingDimensions();
    }

    @Override public String name() { return NAME; }
    @Override public String chatModel() { return "mock-keyword-classifier-v1"; }
    @Override public String embeddingModel() { return "mock-hash-embedding-v1"; }

    @Override
    public ClassificationResult classify(ComplaintText c) {
        String text = (c.title() + " " + c.description()).toLowerCase(Locale.ROOT);
        String language = LanguageDetector.detect(text);

        // A keyword in the title counts double: "Garbage pile near school gate ... on the road" is about garbage.
        String title = c.title() == null ? "" : c.title().toLowerCase(Locale.ROOT);
        String body = c.description() == null ? "" : c.description().toLowerCase(Locale.ROOT);
        Map<Category, Integer> hits = new EnumMap<>(Category.class);
        for (var e : CONCEPT_CATEGORY.entrySet()) {
            int n = 0;
            for (String w : CONCEPTS.get(e.getKey())) {
                if (containsWord(title, w)) n += 2;
                else if (containsWord(body, w)) n += 1;
            }
            if (n > 0) hits.merge(e.getValue(), n, Integer::sum);
        }
        Category best = Category.OTHER;
        int bestHits = 0, secondHits = 0;
        for (var e : hits.entrySet()) {
            if (e.getValue() > bestHits) { secondHits = bestHits; bestHits = e.getValue(); best = e.getKey(); }
            else if (e.getValue() > secondHits) secondHits = e.getValue();
        }
        boolean minor = MINOR.stream().anyMatch(text::contains);
        if (minor && bestHits <= 1) best = Category.OTHER;
        boolean tie = bestHits > 0 && bestHits == secondHits;      // cannot decide: let a human choose
        if (tie) best = Category.OTHER;

        double confidence = tie ? 0.30 : best == Category.OTHER ? (minor ? 0.70 : 0.35)
                : Math.min(0.95, 0.55 + 0.08 * bestHits - 0.06 * secondHits);
        confidence = Math.max(0.30, confidence);

        String priority = minor ? "LOW"
                : URGENT.stream().anyMatch(text::contains) || noWaterForDays(text) ? "HIGH"
                : best == Category.OTHER ? "LOW" : "MEDIUM";

        String summary = language.equals("en")
                ? firstSentence(c.description())
                : "(Mock AI, no translation) " + languageName(language) + " complaint about " + best.label().toLowerCase(Locale.ROOT) + ".";

        RawClassification raw = new RawClassification(best.name(), best.defaultDepartmentCode(), priority, language, summary,
                null, actionsFor(best), confidence);
        int tokens = Math.max(1, text.length() / 4);
        return new ClassificationResult(raw, tokens, 60);
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dimensions];
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> words = tokenize(lower);
        for (String w : words) {
            if (STOPWORDS.contains(w)) continue;
            add(v, "w:" + stem(w), 1.0f);
            for (int i = 0; i + 3 <= w.length(); i++) add(v, "g:" + w.substring(i, i + 3), 0.25f);
        }
        for (var e : CONCEPTS.entrySet()) {          // concepts weigh most and are language independent
            for (String w : e.getValue()) {
                if (containsWord(lower, w)) { add(v, "c:" + e.getKey(), 3.0f); break; }
            }
        }
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    @Override
    public TextResult narrate(WardFacts f) {
        StringBuilder sb = new StringBuilder("(Mock AI narrative) ");
        sb.append(f.wardName()).append(" received ").append(f.received()).append(" complaint(s) in the week starting ")
          .append(f.weekStart()).append(". ");
        if (f.topCategory() != null) sb.append("The largest share is ").append(f.topCategory().toLowerCase(Locale.ROOT).replace('_', ' ')).append(". ");
        sb.append(f.open()).append(f.open() == 1 ? " remains open" : " remain open");
        if (f.overdue() > 0) sb.append(", of which ").append(f.overdue()).append(f.overdue() == 1 ? " is" : " are")
                .append(" past the deadline and need").append(f.overdue() == 1 ? "s" : "").append(" attention");
        sb.append(". ");
        if (f.hotspotCount() > 0) sb.append(f.hotspotCount() == 1 ? "There is 1 location hotspot" : "There are " + f.hotspotCount() + " location hotspots")
                .append(" with repeated complaints.");
        return new TextResult(sb.toString().trim(), 0, 0);
    }

    // ---- helpers -------------------------------------------------------------------
    private void add(float[] v, String feature, float weight) {
        int h = mix(feature.hashCode());
        int idx = Math.floorMod(h, dimensions);
        v[idx] += ((h >>> 31) == 0 ? 1 : -1) * weight;
    }

    private static int mix(int h) {           // spreads String.hashCode bits (MurmurHash3 finalizer)
        h ^= h >>> 16; h *= 0x85ebca6b; h ^= h >>> 13; h *= 0xc2b2ae35; h ^= h >>> 16;
        return h;
    }

    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        for (String t : s.split("[^\\p{L}\\p{M}\\p{N}]+")) if (t.length() > 1) out.add(t);
        return out;
    }

    private static String stem(String w) {
        return w.length() > 4 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w;
    }

    static boolean containsWord(String text, String word) {
        if (word.contains(" ")) return text.contains(word);
        for (String t : tokenize(text)) if (t.equals(word) || stem(t).equals(word)) return true;
        return false;
    }

    private static boolean noWaterForDays(String t) {
        return (t.contains("no water") && (t.contains("day") || t.contains("days"))) || t.contains("ପାଣି ଆସୁନାହିଁ")
                || t.contains("पानी नहीं");
    }

    private static String firstSentence(String d) {
        String s = d.trim();
        int dot = s.indexOf(". ");
        if (dot > 20) s = s.substring(0, dot + 1);
        return s.length() > 200 ? s.substring(0, 197) + "..." : s;
    }

    private static String languageName(String code) {
        return switch (code) { case "hi" -> "Hindi"; case "or" -> "Odia"; default -> "English"; };
    }

    private static String actionsFor(Category c) {
        return switch (c) {
            case ROAD_DAMAGE -> "Inspect the spot, barricade the damaged area, schedule patch repair.";
            case WATER_SUPPLY -> "Check the supply line and valve for the area, arrange a water tanker if supply stays down.";
            case DRAINAGE -> "Send a desilting team, clear the blockage, cover open manholes immediately.";
            case GARBAGE -> "Schedule collection, clear the pile, check the collection route.";
            case STREETLIGHT -> "Send the electrical team to test and replace lamps or secure the pole.";
            case SANITATION -> "Clean and restore water supply to the facility, schedule regular maintenance.";
            case OTHER -> "Review manually and route to the right department.";
        };
    }
}
