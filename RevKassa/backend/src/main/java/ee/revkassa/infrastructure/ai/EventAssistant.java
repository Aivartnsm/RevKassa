package ee.revkassa.infrastructure.ai;

/**
 * The AI assistant for event planning (admins only). The caller builds the prompts from event data only: never member
 * names, emails or balances. The reply text is JSON shaped as ReplySchema; it can still be cut off at the token limit,
 * so the caller parses it defensively.
 */
public interface EventAssistant {

    boolean isEnabled();

    /**
     * @throws AssistantUnavailableException when the assistant is off or the API did not answer
     */
    AssistantReply ask(String systemPrompt, String userPrompt);
}
