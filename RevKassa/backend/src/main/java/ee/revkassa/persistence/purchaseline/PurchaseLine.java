package ee.revkassa.persistence.purchaseline;

import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "purchase_line")
public class PurchaseLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // Copied from the product at the moment of sale: a later price change never rewrites history
    @NotNull
    @Column(name = "unit_price_cents", nullable = false)
    private Long unitPriceCents;

    // The product's purchase price at the moment of sale; null = not set then (the report uses the current one)
    @Column(name = "unit_cost_cents")
    private Long unitCostCents;

}
