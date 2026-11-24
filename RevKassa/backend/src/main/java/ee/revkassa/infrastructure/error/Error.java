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
    TOO_MANY_WRONG_PINS("Too many wrong PINs. Try again later.", 113),
    WRONG_EMAIL_OR_PASSWORD("Wrong email or password.", 114),
    SESSION_MISSING("Session expired or missing.", 115),
    DEVICE_NOT_TRUSTED("PIN login is only allowed on a registered device.", 116),
    TOO_MANY_FAILED_LOGINS("Too many failed logins. Try again later.", 117),
    ACCESS_DENIED("Access denied.", 118),
    DEVICE_ALREADY_REVOKED("Device is already revoked.", 119),
    DEVICE_ALREADY_TRUSTED("This device is already registered.", 120),
    PASSWORD_LINK_INVALID("Password link is invalid or expired.", 121),
    TOO_MANY_PASSWORD_RESETS("Too many password reset requests. Try again later.", 122),
    EMAIL_NOT_SENT("Email could not be sent. Try again later.", 123),
    WRONG_CURRENT_PASSWORD("Current password is wrong.", 124),
    UNEXPECTED_ERROR("Something went wrong, try again", 500);

    private final String message;
    private final Integer errorCode;
}
