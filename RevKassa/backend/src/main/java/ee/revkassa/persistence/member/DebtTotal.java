package ee.revkassa.persistence.member;

/**
 * All negative balances together: how many members owe and how much (positive cents).
 * The sum is null in SQL when nobody owes; it is 0 here.
 */
public record DebtTotal(Long debtorCount, Long owedCents) {

    public DebtTotal {
        owedCents = owedCents == null ? 0L : owedCents;
    }
}
