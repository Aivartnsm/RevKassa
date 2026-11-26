package ee.revkassa.controller.admin.membershipfee.dto;

import ee.revkassa.persistence.membershipcharge.MembershipChargeStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * One member's fee for one month, with the level and amount it was charged with. A WAIVED one also says when, by whom
 * and why it was cancelled.
 */
@Data
public class MembershipChargeDto implements Serializable {
    private Long chargeId;
    private Long memberId;
    private String memberName;
    private String levelName;
    private Long amountCents;
    private MembershipChargeStatusEnum status;
    private OffsetDateTime createdAt;
    private OffsetDateTime waivedAt;
    private String waivedByName;
    private String waiveNote;
}
