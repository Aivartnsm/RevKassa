package ee.revkassa.controller.admin.report.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * One product's sales in the range. revenueCents = units × price at the moment of sale. costCents = units × purchase
 * price stored with the sale (or the product's current one for older sales); costCents and marginCents are null when
 * some units have no purchase price at all (unitsWithoutCost > 0). Margin % = marginCents / revenueCents.
 */
@Data
public class ProductSalesDto implements Serializable {
    private Long productId;
    private String name;
    private Boolean active;
    private long quantity;
    private long revenueCents;
    private Long costCents;
    private Long marginCents;
    private long unitsWithoutCost;
    // Months with sales only, oldest first
    private List<MonthQuantityDto> months;
}
