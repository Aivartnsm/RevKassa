package ee.revkassa.persistence;

import ee.revkassa.persistence.member.DebtTotal;
import ee.revkassa.persistence.payment.PaymentTotal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL sum() is null when no row matches (nobody owes, nothing pending): the overview must show 0, not null.
 */
class TotalsTest {

    @Test
    void debtTotal_withoutDebtors_isZero() {
        assertThat(new DebtTotal(0L, null).owedCents()).isZero();
        assertThat(new DebtTotal(2L, 3250L).owedCents()).isEqualTo(3250);
    }

    @Test
    void paymentTotal_withoutPendingPayments_isZero() {
        assertThat(new PaymentTotal(0L, null).amountCents()).isZero();
        assertThat(new PaymentTotal(1L, 1820L).amountCents()).isEqualTo(1820);
    }
}
