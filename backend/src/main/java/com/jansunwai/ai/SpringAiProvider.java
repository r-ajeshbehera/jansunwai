package com.jansunwai.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jansunwai.config.AppProperties;
import com.jansunwai.repository.DepartmentRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Real LLM provider through Spring AI's OpenAI-compatible client. The same code talks to:
 *  - Google Gemini (free tier available) via its OpenAI-compatible endpoint,
 *  - Ollama (free, runs locally) via http://localhost:11434,
 *  - OpenAI (paid).
 * Only the configuration changes (see docs/ai-design.md). Active when app.ai.provider=spring-ai.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "spring-ai")
public class SpringAiProvider implements AiProvider {
    private final ChatClient chat;
    private final EmbeddingModel embeddings;
    private final DepartmentRepository departments;
    private final ObjectMapper json;
    private final String systemPrompt;
    private final String userPrompt;
    private final String wardPrompt;
    private final String chatModelName;
    private final String embeddingModelName;
    private final int dimensions;

    public SpringAiProvider(ChatModel chatModel, EmbeddingModel embeddingModel, DepartmentRepository departments,
                            ObjectMapper json, AppProperties props,
                            @Value("classpath:prompts/classify-system.st") Resource system,
                            @Value("classpath:prompts/classify-user.st") Resource user,
                            @Value("classpath:prompts/ward-summary.st") Resource ward,
                            @Value("${spring.ai.openai.chat.options.model:unknown}") String chatModelName,
                            @Value("${spring.ai.openai.embedding.options.model:unknown}") String embeddingModelName) {
        this.chat = ChatClient.builder(chatModel).build();
        this.embeddings = embeddingModel;
        this.departments = departments;
        this.json = json;
        this.systemPrompt = read(system);
        this.userPrompt = read(user);
        this.wardPrompt = read(ward);
        this.chatModelName = chatModelName;
        this.embeddingModelName = embeddingModelName;
        this.dimensions = props.ai().embeddingDimensions();
    }

    @Override public String name() { return "spring-ai"; }
    @Override public String chatModel() { return chatModelName; }
    @Override public String embeddingModel() { return embeddingModelName; }

    @Override
    public ClassificationResult classify(ComplaintText c) {
        BeanOutputConverter<RawClassification> converter = new BeanOutputConverter<>(RawClassification.class);
        String deptCodes = departments.findAllByOrderByName().stream().map(d -> d.getCode()).collect(Collectors.joining(", "));
        ChatResponse response = call(() -> chat.prompt()
                .system(s -> s.text(systemPrompt + "\n" + converter.getFormat()).param("departments", deptCodes))
                .user(u -> u.text(userPrompt)
                        .param("title", PiiRedactor.redact(c.title()))
                        .param("description", PiiRedactor.redact(c.description()))
                        .param("language", c.languageHint()))
                .call().chatResponse());
        String text = response.getResult().getOutput().getText();
        RawClassification raw;
        try {
            raw = converter.convert(stripFences(text));
        } catch (RuntimeException e) {
            throw new AiProviderException(AiProviderException.Code.INVALID_OUTPUT, "Model did not return valid JSON", e);
        }
        Usage usage = response.getMetadata().getUsage();
        return new ClassificationResult(raw, usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens());
    }

    @Override
    public float[] embed(String text) {
        float[] v = call(() -> embeddings.embed(PiiRedactor.redact(text)));
        if (v == null || v.length != dimensions) {
            throw new AiProviderException(AiProviderException.Code.DIMENSION_MISMATCH,
                    "Embedding has " + (v == null ? 0 : v.length) + " dimensions, expected " + dimensions);
        }
        return v;
    }

    @Override
    public TextResult narrate(WardFacts facts) {
        String factsJson;
        try {
            factsJson = json.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        ChatResponse response = call(() -> chat.prompt().user(u -> u.text(wardPrompt).param("facts", factsJson)).call().chatResponse());
        Usage usage = response.getMetadata().getUsage();
        return new TextResult(response.getResult().getOutput().getText(),
                usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens());
    }

    /** Maps provider exceptions (HTTP 429, network errors) to our codes. */
    private static <T> T call(java.util.function.Supplier<T> s) {
        try {
            return s.get();
        } catch (AiProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            String msg = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            AiProviderException.Code code = msg.contains("429") || msg.contains("rate limit") || msg.contains("quota")
                    ? AiProviderException.Code.RATE_LIMITED : AiProviderException.Code.UNAVAILABLE;
            throw new AiProviderException(code, "AI provider call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    /** Some models wrap JSON in ```json fences. */
    static String stripFences(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(json)?", "");
            int end = t.lastIndexOf("```");
            if (end >= 0) t = t.substring(0, end);
        }
        return t.trim();
    }

    private static String read(Resource r) {
        try {
            return r.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Map<String, String> describe() {
        return Map.of("chat", chatModelName, "embedding", embeddingModelName);
    }
}
