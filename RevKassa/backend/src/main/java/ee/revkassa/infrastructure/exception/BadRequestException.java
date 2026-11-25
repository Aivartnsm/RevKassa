package ee.revkassa.infrastructure.exception;

import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

/**
 * The request is well-formed but refers to something that does not exist, and only the database can tell (400).
 * Format and range errors are found earlier by Bean Validation.
 */
@Getter
public class BadRequestException extends RuntimeException {

    private final Error error;

    public BadRequestException(Error error) {
        super(error.getMessage());
        this.error = error;
    }
}
