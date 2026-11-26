package ee.revkassa.controller.admin.dashboard.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.YearMonth;
import java.util.List;

/**
 * The admin's overview. Debts are positive "owed" amounts and include inactive members. Stock figures count only
 * products for sale: lowStockCount = stock 0 up to the warning level, negativeStockCount = below 0 (needs a count).
 */
@Data
public class DashboardDto implements Serializable {
    private long openDebtCents;
    private long debtorCount;
    private long pendingPaymentCount;
    private long pendingPaymentCents;
    private long lowStockCount;
    private long negativeStockCount;
    // Negative first, then lowest stock
    private List<StockToCheckDto> stockToCheck;
    // Largest debt first
    private List<DebtorDto> largestDebts;
    // The month of topProducts ("Enim ostetud sel kuul")
    private YearMonth month;
    // Most units sold this month first
    private List<TopProductDto> topProducts;
}
