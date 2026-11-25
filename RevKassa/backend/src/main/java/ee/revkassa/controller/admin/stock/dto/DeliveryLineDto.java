package ee.revkassa.controller.admin.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;

@Data
public class DeliveryLineDto implements Serializable {

    @NotNull
    private Long productId;

    @NotNull
    @Positive
    @Max(10_000)
    private Integer quantity;

    // Optional purchase price per unit; 0 for free goods
    @PositiveOrZero
    @Max(1_000_000)
    private Long unitCostCents;
}
