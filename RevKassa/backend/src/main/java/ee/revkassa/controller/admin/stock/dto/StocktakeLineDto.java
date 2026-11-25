package ee.revkassa.controller.admin.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;

@Data
public class StocktakeLineDto implements Serializable {

    @NotNull
    private Long productId;

    // What is actually in the fridge
    @NotNull
    @PositiveOrZero
    @Max(100_000)
    private Integer countedQty;
}
