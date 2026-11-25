package ee.revkassa.persistence.stockmovement;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

/**
 * One row of the stock ledger. Rows are only added, never changed or deleted: a mistake is fixed with a new row.
 */
@Getter
@Setter
@Entity
@Table(name = "stock_movement")
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = Integer.MAX_VALUE)
    private StockMovementTypeEnum type;

    @NotNull
    @Column(name = "quantity_delta", nullable = false)
    private Integer quantityDelta;

    // Purchase price, for DELIVERY
    @Column(name = "unit_cost_cents")
    private Long unitCostCents;

    // For SALE and SALE_REVERSAL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @Column(name = "note", length = Integer.MAX_VALUE)
    private String note;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Member createdBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
