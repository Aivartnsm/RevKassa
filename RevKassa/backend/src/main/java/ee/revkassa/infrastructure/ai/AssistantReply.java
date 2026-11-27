package ee.revkassa.infrastructure.ai;

/**
 * The assistant's answer and the tokens it cost (logged by the caller; the text itself is never logged).
 */
public record AssistantReply(String text, int inputTokens, int outputTokens) {
}
