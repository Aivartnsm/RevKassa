package ee.revkassa.infrastructure.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Error {

    ENTRY_NOT_FOUND("Record not found", 101),
    DATA_NOT_FOUND("Data not found", 102),
    INVALID_DATA_IN_FIELDS("Validate fields", 103),
    INVALID_REQUEST("Invalid request. Try again", 104),
    DATA_HAS_CHANGED_RELOAD_REQUIRED("Oops, someone has changed the data, try reloading the page", 105),
    WRONG_PIN("Wrong PIN.", 111),
    ACCOUNT_NOT_ACTIVE("Account is not active.", 112),
    WRONG_EMAIL_OR_PASSWORD("Wrong email or password.", 114),
    SESSION_MISSING("Session expired or missing.", 115),
    DEVICE_NOT_TRUSTED("PIN login is only allowed on a registered device.", 116),
    ACCESS_DENIED("Access denied.", 118),
    UNEXPECTED_ERROR("Something went wrong, try again", 500);

    private final String message;
    private final Integer errorCode;
}
