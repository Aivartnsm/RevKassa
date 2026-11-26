package ee.revkassa.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceEmailTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0          | 0,00 €",
            "5          | 0,05 €",
            "2740       | 27,40 €",
            "-2740      | −27,40 €",
            "123456     | 1 234,56 €",
            "-100000000 | −1 000 000,00 €"
    })
    void formatEuro_writesEstonianFormat(long cents, String expected) {
        assertThat(InvoiceEmail.formatEuro(cents)).isEqualTo(expected);
    }
}
