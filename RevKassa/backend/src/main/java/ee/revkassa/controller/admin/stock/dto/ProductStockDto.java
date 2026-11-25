package ee.revkassa.controller.admin.stock.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * A product's stock after a stock change.
 */
@Data
public class ProductStockDto implements Serializable {
    private Long productId;
    private String name;
    private Integer stockQty;
}
