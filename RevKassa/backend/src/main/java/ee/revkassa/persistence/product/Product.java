package ee.revkassa.persistence.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;

// Only changed columns are written: saving a product form must never write back a stale stock_qty
@DynamicUpdate
@Getter
@Setter
@Entity
@Table(name = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "style", length = Integer.MAX_VALUE)
    private String style;

    @Column(name = "volume_ml")
    private Integer volumeMl;

    @NotNull
    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    // Current purchase price, admin only; null = not set. Copied to each sale line for the margin
    @Column(name = "cost_cents")
    private Long costCents;

    // Cache of sum(stock_movement.quantity_delta); changed only by StockService.move with one SQL update,
    // so this field can be out of date in memory after a move in the same transaction
    @NotNull
    @ColumnDefault("0")
    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty;

    @NotNull
    @ColumnDefault("6")
    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "active", nullable = false)
    private Boolean active;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
