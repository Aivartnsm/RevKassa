package ee.revkassa.controller.admin.event.dto;

import ee.revkassa.persistence.eventbudgetline.BudgetLineSourceEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * source: AI when the admin accepts an assistant suggestion; MANUAL when missing. It cannot be changed later.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BudgetLineCreateRequestDto extends BudgetLineUpdateRequestDto {
    private BudgetLineSourceEnum source;
}
