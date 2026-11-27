package ee.revkassa.persistence.eventbudgetline;

/**
 * One event's planned costs and the actual costs filled in so far (lines without an actual amount add nothing).
 */
public record BudgetTotal(Long eventId, long plannedCents, long actualCents) {

    public static BudgetTotal none(Long eventId) {
        return new BudgetTotal(eventId, 0, 0);
    }
}
