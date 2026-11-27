package ee.revkassa.controller.admin.event.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * The event's planning sheet. Every category is listed, in a fixed order, also without lines.
 */
@Data
public class EventBudgetDto implements Serializable {
    private List<BudgetCategoryDto> categories;
    private long plannedCents;
    // Filled-in actual amounts only
    private long actualCents;
    // Lines whose actual amount is still empty
    private long missingActualCount;
    // The fees of the active participants, charged or not
    private long feeIncomeCents;
    // feeIncomeCents - plannedCents
    private long plannedResultCents;
    // feeIncomeCents - actualCents
    private long actualResultCents;
}
