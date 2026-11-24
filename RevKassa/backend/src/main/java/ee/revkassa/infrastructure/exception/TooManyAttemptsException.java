package ee.revkassa.infrastructure.exception;

import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

/**
 * Too many attempts or requests (429). retryAfterSeconds goes to the Retry-After header. For login lockouts
 * nextLockoutSeconds tells the UI how long the next wrong attempt after this lockout will lock (0 = not a lockout).
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

    public TooManyAttemptsException(Error error, long retryAfterSeconds) {
        this(error, retryAfterSeconds, 0);
    }
}
