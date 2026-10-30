package ee.revkassa.infrastructure.exception;


import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {

    private final Error error;

    public ForbiddenException(Error error) {
        super(error.getMessage());
        this.error = error;
    }
}
