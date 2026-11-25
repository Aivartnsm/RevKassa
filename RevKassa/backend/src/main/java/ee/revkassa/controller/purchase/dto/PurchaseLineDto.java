package ee.revkassa.controller.purchase.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PurchaseLineDto implements Serializable {
    private Long productId;
    private String name;
    private Integer quantity;
    private Long unitPriceCents;
    private Long lineTotalCents;
}
