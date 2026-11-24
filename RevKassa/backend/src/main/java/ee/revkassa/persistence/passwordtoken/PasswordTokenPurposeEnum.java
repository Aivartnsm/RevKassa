package ee.revkassa.persistence.passwordtoken;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum PasswordTokenPurposeEnum {
    // New account: the member sets a first password
    INVITE(Duration.ofDays(7)),
    // "Forgot password?"
    RESET(Duration.ofHours(1));

    private final Duration validity;
}
