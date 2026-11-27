package ee.revkassa.controller.admin.assistant.dto;

import ee.revkassa.persistence.eventbudgetline.BudgetCategoryEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A budget line the assistant suggests, already valid for POST .../budget-lines (with source AI). Nothing is added
 * until the admin accepts it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedLineDto implements Serializable {
    private BudgetCategoryEnum category;
    private String description;
    private long plannedCents;
}
