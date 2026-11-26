package ee.revkassa.infrastructure.security;

import ee.revkassa.infrastructure.error.Error;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What is being guessed. Each type has its own counters and its own error codes.
 */
@Getter
@RequiredArgsConstructor
public enum LockoutTypeEnum {
    PIN_LOGIN(Error.WRONG_PIN, Error.TOO_MANY_WRONG_PINS),
    PASSWORD_LOGIN(Error.WRONG_EMAIL_OR_PASSWORD, Error.TOO_MANY_FAILED_LOGINS),
    PASSWORD_CHANGE(Error.WRONG_CURRENT_PASSWORD, Error.TOO_MANY_FAILED_LOGINS),
    PIN_CHANGE(Error.WRONG_CURRENT_PIN, Error.TOO_MANY_WRONG_PINS);

    private final Error wrongAttemptError;
    private final Error lockedError;
}
