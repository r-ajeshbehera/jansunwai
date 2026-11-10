package com.jansunwai.ai;

/**
 * The only thing business code knows about AI. Implementations: MockAiProvider (offline, default) and
 * SpringAiProvider (Gemini / Ollama / OpenAI via Spring AI). Swapping providers never touches business logic.
 * All outputs are treated as UNTRUSTED and validated by AiOutputValidator before use.
 */
public interface AiProvider {
    String name();

    String chatModel();

    String embeddingModel();

    /** Classify, detect language, summarise and (if needed) translate a complaint. */
    ClassificationResult classify(ComplaintText complaint);

    /** Vector representation of the text for semantic similarity (must have app.ai.embedding-dimensions values). */
    float[] embed(String text);

    /** A short narrative for a ward, using ONLY the facts given. */
    TextResult narrate(WardFacts facts);

    record ComplaintText(String title, String description, String languageHint) { }

    /** Raw, unvalidated provider output plus usage numbers. */
    record ClassificationResult(RawClassification raw, Integer inputTokens, Integer outputTokens) { }

    record TextResult(String text, Integer inputTokens, Integer outputTokens) { }
}
