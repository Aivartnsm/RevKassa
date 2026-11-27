package ee.revkassa.infrastructure.ai;

import ee.revkassa.infrastructure.error.Error;

/**
 * Used when no API key is set: the UI hides the assistant, and a call that gets here anyway is refused without
 * touching the network.
 */
public class DisabledEventAssistant implements EventAssistant {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public AssistantReply ask(String systemPrompt, String userPrompt) {
        throw new AssistantUnavailableException(Error.ASSISTANT_DISABLED);
    }
}
