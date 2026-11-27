package ee.revkassa.infrastructure.ai;

import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

/**
 * The assistant is off (ASSISTANT_DISABLED) or the API failed, timed out or ran out of funds (ASSISTANT_FAILED).
 * Answered with 503: the admin can try again, nothing was saved.
 */
@Getter
public class AssistantUnavailableException extends RuntimeException {

    private final Error error;

    public AssistantUnavailableException(Error error) {
        super(error.getMessage());
        this.error = error;
    }
}
