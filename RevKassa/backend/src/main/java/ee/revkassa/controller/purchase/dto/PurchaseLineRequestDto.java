package ee.revkassa.controller.purchase.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * Only the product and quantity come from the client; the price always comes from the server.
 */
@Data
public class PurchaseLineRequestDto implements Serializable {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    @Max(99)
    private Integer quantity;
}
