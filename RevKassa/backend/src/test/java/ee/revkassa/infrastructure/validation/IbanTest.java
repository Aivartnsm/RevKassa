package ee.revkassa.infrastructure.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IbanTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "EE382200221020145685",          // Estonia, Swedbank example
            "ee38 2200 2210 2014 5685",      // as typed: spaces and lower case
            "  EE38 2200 2210 2014 5685  ",
            "DE89370400440532013000",        // Germany
            "GB82WEST12345698765432",        // letters inside the account part
            "NO9386011117947"})              // shortest country format (15)
    void validIban_isAccepted(String iban) {
        assertThat(Iban.isValid(iban)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "EE382200221020145686",          // last digit wrong: check fails
            "EE392200221020145685",          // check digits wrong
            "EE38220022102014568",           // one digit missing
            "EE3822002210201456850000000000000000",  // longer than 34
            "3838 2200 2210 2014 5685",      // no country code
            "EE38-2200-2210-2014-5685",      // dashes
            "EE38 2200 2210 2014 568Ä",
            "",
            "   "})
    void invalidIban_isRefused(String iban) {
        assertThat(Iban.isValid(iban)).isFalse();
    }

    @Test
    void normalize_removesSpacesAndUsesCapitals() {
        assertThat(Iban.normalize(" ee38 2200\t2210 2014 5685 ")).isEqualTo("EE382200221020145685");
    }

    @Test
    void group_printsByFourAsBanksDo() {
        assertThat(Iban.group("EE382200221020145685")).isEqualTo("EE38 2200 2210 2014 5685");
        assertThat(Iban.group("NO9386011117947")).isEqualTo("NO93 8601 1117 947");
    }
}
