package ee.revkassa.controller.admin.stock.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * One counted product: stock before the count, the count and the correction written (0 = no movement).
 */
@Data
public class StocktakeResultDto implements Serializable {
    private Long productId;
    private String name;
    private Integer previousStockQty;
    private Integer countedQty;
    private Integer difference;
}
