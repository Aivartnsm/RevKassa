package ee.revkassa.persistence.purchaseline;

// One product's completed sales in one month ("2026-11"); costCents covers only units with a known purchase price
public interface ProductMonthSales {
    Long getProductId();

    String getMonth();

    Long getQuantity();

    Long getRevenueCents();

    Long getCostCents();

    Long getUnitsWithoutCost();
}
