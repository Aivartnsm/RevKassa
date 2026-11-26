package ee.revkassa.persistence.payment;

// The sum is null in SQL when there are no rows; it is 0 here
public record PaymentTotal(Long count, Long amountCents) {

    public PaymentTotal {
        amountCents = amountCents == null ? 0L : amountCents;
    }
}
