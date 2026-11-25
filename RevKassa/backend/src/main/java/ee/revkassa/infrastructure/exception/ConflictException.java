package ee.revkassa.infrastructure.exception;

import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

/**
 * The request is valid, but it was made against data that has changed since (409); the client reloads and asks again.
 */
@Getter
public class ConflictException extends RuntimeException {

    private final Error error;

    public ConflictException(Error error) {
        super(error.getMessage());
        this.error = error;
    }
}
