package ee.revkassa.infrastructure.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"Parool12!x", "Uus-Parool-579", "Kolm kassi aias!", "Õun#põõsas7", "Aa1!Bb2@Cc"})
    void validPasswords_haveNoViolation(String password) {
        assertThat(PasswordRules.findViolation(password)).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            // the examples that must not pass
            "paroooool123!    | must contain an uppercase letter",
            "Paroooool12!     | must not have the same character more than 2 times in a row",
            "parool123!       | must contain an uppercase letter",
            "Parool123!       | must not have more than 2 letters or digits in sequence (abc, 123)",
            "123456789abcd!   | must contain an uppercase letter",
            "123456789Abcd!   | must not have more than 2 letters or digits in sequence (abc, 123)",
            // one rule each
            "Lühike1!         | must be at least 10 characters",
            "PAROOL-KASS!     | must contain a lowercase letter",
            "ParoolKass2      | must contain a special character",
            "Parool Kass2     | must contain a special character",
            "Kass!2aAa-x      | must not have the same character more than 2 times in a row",
            "Kass!2xYz-p      | must not have more than 2 letters or digits in sequence (abc, 123)",
            "Kass!2CbA-p      | must not have more than 2 letters or digits in sequence (abc, 123)",
            "Kass!987-pX      | must not have more than 2 letters or digits in sequence (abc, 123)"
    })
    void brokenRule_isNamed(String password, String expectedViolation) {
        assertThat(PasswordRules.findViolation(password)).contains(expectedViolation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Kass!12-Py", "Kass!9:;-Py", "Kass!ab-Py", "Kass!oo-Py"})
    void twoInARowOrMixedKinds_areAllowed(String password) {
        assertThat(PasswordRules.findViolation(password)).isEmpty();
    }

    @Test
    void exactlySeventyTwoBytes_isAllowed() {
        // 2 + 35 × 2 bytes ("ä" and "ö" are 2 bytes each in UTF-8)
        assertThat(PasswordRules.findViolation("A!" + "äö".repeat(17) + "ä")).isEmpty();
    }

    @Test
    void overSeventyTwoBytes_isRefused() {
        assertThat(PasswordRules.findViolation("A!b" + "äö".repeat(17) + "ä")).contains("must be at most 72 bytes");
    }
}
