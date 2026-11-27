package ee.revkassa.infrastructure.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import ee.revkassa.infrastructure.error.Error;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two assistants without Google: request settings, reply parsing and failures. The only network call goes to a
 * closed local port.
 */
@ExtendWith(OutputCaptureExtension.class)
class EventAssistantTest {

    private static final AiProperties PROPERTIES =
            new AiProperties("test-key", "gemini-3.5-flash-lite", 4000, Duration.ofSeconds(60), 2);
    private static final String SECRET_QUESTION = "Mitu kasti Sakut osta 40 inimesele?";

    @Test
    void disabled_refusesWithoutCallingAnything() {
        DisabledEventAssistant assistant = new DisabledEventAssistant();

        assertThat(assistant.isEnabled()).isFalse();
        assertThatThrownBy(() -> assistant.ask("system", SECRET_QUESTION))
                .isInstanceOf(AssistantUnavailableException.class)
                .extracting("error").isEqualTo(Error.ASSISTANT_DISABLED);
    }

    @Test
    void gemini_httpOptions_timeoutInMillisAndOneRetry() {
        HttpOptions options = GeminiEventAssistant.httpOptions(PROPERTIES);

        assertThat(options.timeout()).contains(60_000);
        assertThat(options.retryOptions()).hasValueSatisfying(retry -> assertThat(retry.attempts()).contains(2));
    }

    @Test
    void gemini_request_asksForJsonInTheReplyShape_withLimitAndSystemPrompt() {
        try (GeminiEventAssistant assistant = new GeminiEventAssistant(PROPERTIES)) {
            GenerateContentConfig config = assistant.configWith("Oled ürituste planeerija.");

            assertThat(config.responseMimeType()).contains("application/json");
            assertThat(config.responseJsonSchema()).contains(ReplySchema.JSON_SCHEMA);
            assertThat(config.maxOutputTokens()).contains(4000);
            assertThat(config.systemInstruction().orElseThrow().text()).isEqualTo("Oled ürituste planeerija.");
        }
    }

    @Test
    void gemini_reply_textAndTokenCounts() {
        try (GeminiEventAssistant assistant = new GeminiEventAssistant(PROPERTIES)) {
            AssistantReply reply = assistant.toReply(GenerateContentResponse.fromJson("""
                    {"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"answer\\":\\"Osta 3 kasti\\",\\"suggestedLines\\":[]}"}]},
                      "finishReason":"STOP"}],
                     "usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":45,"totalTokenCount":165}}"""));

            assertThat(reply.text()).isEqualTo("{\"answer\":\"Osta 3 kasti\",\"suggestedLines\":[]}");
            assertThat(reply.inputTokens()).isEqualTo(120);
            assertThat(reply.outputTokens()).isEqualTo(45);
        }
    }

    @Test
    void gemini_replyCutAtTheTokenLimit_stillReturnsItsText() {
        try (GeminiEventAssistant assistant = new GeminiEventAssistant(PROPERTIES)) {
            AssistantReply reply = assistant.toReply(GenerateContentResponse.fromJson("""
                    {"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"answer\\":\\"Osta"}]},
                      "finishReason":"MAX_TOKENS"}]}"""));

            assertThat(reply.text()).isEqualTo("{\"answer\":\"Osta");
            assertThat(reply.inputTokens()).isZero();
        }
    }

    @Test
    void gemini_replyWithoutText_isAFailure() {
        try (GeminiEventAssistant assistant = new GeminiEventAssistant(PROPERTIES)) {
            GenerateContentResponse empty = GenerateContentResponse.fromJson(
                    "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[]},\"finishReason\":\"STOP\"}]}");

            assertThatThrownBy(() -> assistant.toReply(empty))
                    .isInstanceOf(AssistantUnavailableException.class)
                    .extracting("error").isEqualTo(Error.ASSISTANT_FAILED);
        }
    }

    @Test
    void gemini_replyBlockedBySafetyFilter_isAFailure() {
        try (GeminiEventAssistant assistant = new GeminiEventAssistant(PROPERTIES)) {
            GenerateContentResponse blocked = GenerateContentResponse.fromJson(
                    "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");

            assertThatThrownBy(() -> assistant.toReply(blocked))
                    .isInstanceOf(AssistantUnavailableException.class)
                    .extracting("error").isEqualTo(Error.ASSISTANT_FAILED);
        }
    }

    @Test
    void gemini_unreachableApi_isAFailure_andThePromptIsNotLogged(CapturedOutput output) {
        Client closedPort = Client.builder().apiKey("test-key").httpOptions(HttpOptions.builder()
                        .baseUrl("http://127.0.0.1:1")
                        .timeout(2_000)
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
        try (GeminiEventAssistant assistant = new GeminiEventAssistant(closedPort, PROPERTIES)) {
            assertThatThrownBy(() -> assistant.ask("Süsteem", SECRET_QUESTION))
                    .isInstanceOf(AssistantUnavailableException.class)
                    .extracting("error").isEqualTo(Error.ASSISTANT_FAILED);
        }
        assertThat(output).contains("Event assistant API").doesNotContain(SECRET_QUESTION).doesNotContain("test-key");
    }
}
