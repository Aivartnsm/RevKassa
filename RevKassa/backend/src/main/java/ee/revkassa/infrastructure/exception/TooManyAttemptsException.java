package ee.revkassa.infrastructure.exception;

import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

/**
 * Login is locked after too many wrong attempts (429). retryAfterSeconds goes to the Retry-After header,
 * nextLockoutSeconds tells the UI how long the next wrong attempt after this lockout will lock.
 */
@Getter
public class TooManyAttemptsException extends RuntimeException {

    private final Error error;
    private final long retryAfterSeconds;
    private final long nextLockoutSeconds;

    public TooManyAttemptsException(Error error, long retryAfterSeconds, long nextLockoutSeconds) {
        super(error.getMessage());
        this.error = error;
        this.retryAfterSeconds = retryAfterSeconds;
        this.nextLockoutSeconds = nextLockoutSeconds;
    }
}
