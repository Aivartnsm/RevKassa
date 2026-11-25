package ee.revkassa.controller.admin.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

@Data
public class WriteOffLineDto implements Serializable {

    @NotNull
    private Long productId;

    // How many units are removed (broken, expired); positive, the ledger stores it as negative
    @NotNull
    @Positive
    @Max(10_000)
    private Integer quantity;
}
