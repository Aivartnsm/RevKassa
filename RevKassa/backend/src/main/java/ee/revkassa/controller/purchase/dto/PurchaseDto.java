package ee.revkassa.controller.purchase.dto;

import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A recorded purchase with the member's balance after it. undoableUntil is server time: after it the member can no
 * longer undo the purchase (B12).
 */
@Data
public class PurchaseDto implements Serializable {
    private Long purchaseId;
    private PurchaseStatusEnum status;
    private Long totalCents;
    private Long balanceCents;
    private OffsetDateTime createdAt;
    private OffsetDateTime undoableUntil;
    private List<PurchaseLineDto> lines;
}
