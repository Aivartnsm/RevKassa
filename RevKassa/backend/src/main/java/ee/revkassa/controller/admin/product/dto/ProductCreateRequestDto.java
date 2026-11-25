package ee.revkassa.controller.admin.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Stock is not here: it changes only with deliveries, stock counts and sales (stock ledger).
 */
@Data
public class ProductCreateRequestDto implements Serializable {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 50)
    private String style;

    @Positive
    @Max(10_000)
    private Integer volumeMl;

    @NotNull
    @Positive
    private Long priceCents;

    @NotNull
    @PositiveOrZero
    @Max(10_000)
    private Integer lowStockThreshold;

    @NotNull
    @PositiveOrZero
    @Max(10_000)
    private Integer sortOrder;

    @NotNull
    private Boolean active;
}
