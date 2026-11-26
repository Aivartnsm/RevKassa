package ee.revkassa.controller.admin.report.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Completed sales between from and to (both days included, club time). products: sold ones by units (most first),
 * then active products that sold nothing (0). Cost and margin totals cover only products whose every unit has a known
 * purchase price; unitsWithoutCost says how many units were left out of them.
 */
@Data
public class SalesReportDto implements Serializable {
    private LocalDate from;
    private LocalDate to;
    // Every month the range touches, for the "Kuude kaupa" column
    private List<YearMonth> months;
    private List<ProductSalesDto> products;
    private long totalQuantity;
    private long totalRevenueCents;
    private long totalCostCents;
    private long totalMarginCents;
    private long unitsWithoutCost;
}
