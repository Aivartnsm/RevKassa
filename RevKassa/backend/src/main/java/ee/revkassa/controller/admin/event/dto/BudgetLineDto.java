package ee.revkassa.controller.admin.event.dto;

import ee.revkassa.persistence.eventbudgetline.BudgetCategoryEnum;
import ee.revkassa.persistence.eventbudgetline.BudgetLineSourceEnum;
import lombok.Data;

import java.io.Serializable;

@Data
public class BudgetLineDto implements Serializable {
    private Long lineId;
    private BudgetCategoryEnum category;
    private String description;
    private Long plannedCents;
    // null = not filled in yet
    private Long actualCents;
    private String note;
    // AI = an accepted suggestion of the assistant
    private BudgetLineSourceEnum source;
}
