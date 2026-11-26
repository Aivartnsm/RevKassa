package ee.revkassa.controller.admin.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * A product as the admin sees and edits it. version must be sent back unchanged with an update.
 */
@Data
public class AdminProductDto implements Serializable {
    private Long id;
    private String name;
    private String style;
    private Integer volumeMl;
    private Long priceCents;
    private Long costCents;
    private Integer stockQty;
    private Integer lowStockThreshold;
    private Integer sortOrder;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
}
