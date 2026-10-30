package ee.revkassa.infrastructure.exception;


import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

@Getter
public class DataNotFoundException extends RuntimeException {

    private final Error error;

    public DataNotFoundException(Error error) {
        super(error.getMessage());
        this.error = error;
    }
}
