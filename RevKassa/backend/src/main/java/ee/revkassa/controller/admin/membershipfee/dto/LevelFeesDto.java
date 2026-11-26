package ee.revkassa.controller.admin.membershipfee.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * A level's monthly fee: the amount for the current month (0 if never set), the next scheduled change (null if none)
 * and every row newest first, scheduled ones included.
 */
@Data
public class LevelFeesDto implements Serializable {
    private Long levelId;
    private String name;
    private Boolean active;
    private Long currentAmountCents;
    private MembershipFeeDto upcoming;
    private List<MembershipFeeDto> history;
}
