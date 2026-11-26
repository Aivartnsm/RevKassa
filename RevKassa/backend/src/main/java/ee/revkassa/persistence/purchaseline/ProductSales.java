package ee.revkassa.persistence.purchaseline;

// Units of one product sold in a period
public record ProductSales(Long productId, String name, Long quantity) {
}
