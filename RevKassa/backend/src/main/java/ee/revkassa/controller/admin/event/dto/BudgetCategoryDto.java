package ee.revkassa.controller.admin.event.dto;

import ee.revkassa.persistence.eventbudgetline.BudgetCategoryEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetCategoryDto implements Serializable {
    private BudgetCategoryEnum category;
    private long plannedCents;
    // Filled-in actual amounts only
    private long actualCents;
    private List<BudgetLineDto> lines;
}
