package ee.revkassa.controller.product.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * A product in the member's purchase view.
 */
@Data
public class ProductDto implements Serializable {
    private Long id;
    private String name;
    private String style;
    private Integer volumeMl;
    private Long priceCents;
    private Integer stockQty;
}
