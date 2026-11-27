package ee.revkassa.infrastructure.ai;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import ee.revkassa.infrastructure.error.Error;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The event assistant on Google Gemini (Google Gen AI Java SDK). The reply is asked for as JSON in ReplySchema's
 * shape. Prompts and replies are never logged; a failure is logged with its HTTP code only (e.g. funds ran out),
 * and the admin sees "Abiline ei vastanud". Closed by Spring at shutdown.
 */
@Slf4j
public class GeminiEventAssistant implements EventAssistant, AutoCloseable {

    private static final String JSON = "application/json";
    private static final Set<FinishReason.Known> ANSWERED = EnumSet.of(FinishReason.Known.STOP,
            FinishReason.Known.MAX_TOKENS, FinishReason.Known.FINISH_REASON_UNSPECIFIED, FinishReason.Known.CONTINUATION);

    private final Client client;
    private final GenerateContentConfig requestConfig;
    private final String model;

    public GeminiEventAssistant(AiProperties properties) {
        this(createClient(properties), properties);
    }

    // Tests pass a client pointing elsewhere
    GeminiEventAssistant(Client client, AiProperties properties) {
        this.client = client;
        this.model = properties.model();
        this.requestConfig = GenerateContentConfig.builder()
                .maxOutputTokens(properties.maxOutputTokens())
                .responseMimeType(JSON)
                .responseJsonSchema(ReplySchema.JSON_SCHEMA)
                .build();
    }

    // The key is passed explicitly: without it the SDK would read GOOGLE_API_KEY from the environment by itself
    static Client createClient(AiProperties properties) {
        return Client.builder()
                .apiKey(properties.apiKey())
                .httpOptions(httpOptions(properties))
                .build();
    }

    static HttpOptions httpOptions(AiProperties properties) {
        return HttpOptions.builder()
                .timeout(Math.toIntExact(properties.timeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(properties.maxAttempts()).build())
                .build();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public AssistantReply ask(String systemPrompt, String userPrompt) {
        GenerateContentResponse response;
        try {
            response = client.models.generateContent(model, userPrompt, configWith(systemPrompt));
        } catch (ApiException e) {
            // The code and status say why (429 quota or funds, 400, 5xx); the message may quote the request
            log.warn("Event assistant API call failed: code={}, status={}", e.code(), e.status());
            throw new AssistantUnavailableException(Error.ASSISTANT_FAILED);
        } catch (GenAiIOException e) {
            log.warn("Event assistant API not reachable or timed out");
            throw new AssistantUnavailableException(Error.ASSISTANT_FAILED);
        }
        return toReply(response);
    }

    GenerateContentConfig configWith(String systemPrompt) {
        return requestConfig.toBuilder()
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                .build();
    }

    // A reply cut off at the token limit (MAX_TOKENS) still comes back: the caller falls back to plain text. A blocked
    // one (SAFETY, RECITATION, ...) or one without text is a failure; the SDK itself would throw on reading its text
    AssistantReply toReply(GenerateContentResponse response) {
        FinishReason finishReason = response.finishReason();
        String text = ANSWERED.contains(finishReason.knownEnum()) ? response.text() : null;
        if (text == null || text.isBlank()) {
            log.warn("Event assistant returned no answer: finishReason={}", finishReason);
            throw new AssistantUnavailableException(Error.ASSISTANT_FAILED);
        }
        Optional<GenerateContentResponseUsageMetadata> usage = response.usageMetadata();
        return new AssistantReply(text,
                usage.flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).orElse(0),
                usage.flatMap(GenerateContentResponseUsageMetadata::candidatesTokenCount).orElse(0));
    }

    @Override
    public void close() {
        client.close();
    }
}
