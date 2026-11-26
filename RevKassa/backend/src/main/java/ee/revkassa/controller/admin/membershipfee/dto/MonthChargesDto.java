package ee.revkassa.controller.admin.membershipfee.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.YearMonth;
import java.util.List;

/**
 * A month's fees by member name, with totals: charged = still owed as fees, waived = cancelled.
 */
@Data
public class MonthChargesDto implements Serializable {
    private YearMonth month;
    private List<MembershipChargeDto> charges;
    private long chargedCount;
    private long chargedTotalCents;
    private long waivedCount;
    private long waivedTotalCents;
}
