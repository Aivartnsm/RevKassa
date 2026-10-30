package ee.revkassa.infrastructure.exception;


import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

@Getter
public class PrimaryKeyNotFoundException extends RuntimeException {

    private final Error error;

    public PrimaryKeyNotFoundException(Error error) {
        super(error.getMessage());
        this.error = error;
    }
}
