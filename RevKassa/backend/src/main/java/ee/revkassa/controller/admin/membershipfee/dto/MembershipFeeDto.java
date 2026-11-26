package ee.revkassa.controller.admin.membershipfee.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * One fee row: the level's fee from validFrom ("2026-11") on, until its next row.
 */
@Data
public class MembershipFeeDto implements Serializable {
    private YearMonth validFrom;
    private Long amountCents;
    private String createdByName;
    private OffsetDateTime createdAt;
}
