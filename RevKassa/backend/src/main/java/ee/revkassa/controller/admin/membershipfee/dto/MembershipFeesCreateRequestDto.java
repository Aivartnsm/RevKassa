package ee.revkassa.controller.admin.membershipfee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.YearMonth;
import java.util.List;

/**
 * New fees for some or all levels from validFrom ("2026-12") on. Levels that are not listed keep their fees.
 */
@Data
public class MembershipFeesCreateRequestDto implements Serializable {

    public static final int MAX_LEVELS = 100;

    @NotNull
    private YearMonth validFrom;

    @NotEmpty
    @Size(max = MAX_LEVELS)
    private List<@NotNull @Valid LevelFeeRequestDto> fees;

    // Reported as field "levelIdsUnique" in ApiError.fieldErrors
    @AssertTrue(message = "a level is listed more than once")
    @SuppressWarnings("unused") // called by Bean Validation
    private boolean isLevelIdsUnique() {
        if (fees == null || fees.stream().anyMatch(fee -> fee == null || fee.getLevelId() == null)) {
            return true;
        }
        return fees.stream().map(LevelFeeRequestDto::getLevelId).distinct().count() == fees.size();
    }
}
