package ee.revkassa.controller.admin.dashboard.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class StockToCheckDto implements Serializable {
    private Long productId;
    private String name;
    private Integer stockQty;
    private Integer lowStockThreshold;
    // LOW = "Vähe", NEGATIVE = "Vajab inventuuri"
    private StockCheckStatusEnum status;
}
