package ee.revkassa.controller.admin.member.dto;

import ee.revkassa.controller.purchase.dto.PurchaseLineDto;
import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One purchase of a member as the admin sees it. A REVERSED one also says when and by whom it was undone.
 */
@Data
public class MemberPurchaseDto implements Serializable {
    private Long purchaseId;
    private OffsetDateTime createdAt;
    private PurchaseStatusEnum status;
    private Long totalCents;
    private List<PurchaseLineDto> lines;
    private OffsetDateTime reversedAt;
    private String reversedByName;
}
