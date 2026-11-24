package ee.revkassa.infrastructure.exception;


import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {

    private final Error error;
    // Wrong PIN / password before lockout: how many more wrong attempts are allowed (ApiError.remainingAttempts)
    private final Integer remainingAttempts;

    public ForbiddenException(Error error) {
        this(error, null);
    }

    public ForbiddenException(Error error, Integer remainingAttempts) {
        super(error.getMessage());
        this.error = error;
        this.remainingAttempts = remainingAttempts;
    }
}
