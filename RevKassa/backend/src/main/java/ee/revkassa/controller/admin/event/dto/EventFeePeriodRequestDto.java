package ee.revkassa.controller.admin.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * One price period: from validFrom (Tallinn date, null = from the start) until the next period. A level that is not
 * listed pays 0 in this period.
 */
@Data
public class EventFeePeriodRequestDto implements Serializable {

    public static final int MAX_LEVELS = 100;

    private LocalDate validFrom;

    @NotNull
    @Size(max = MAX_LEVELS)
    private List<@NotNull @Valid EventFeeRequestDto> fees;

    // Reported as field "periods[n].levelIdsUnique" in ApiError.fieldErrors
    @AssertTrue(message = "a level is listed more than once")
    @SuppressWarnings("unused") // called by Bean Validation
    private boolean isLevelIdsUnique() {
        if (fees == null || fees.stream().anyMatch(fee -> fee == null || fee.getLevelId() == null)) {
            return true;
        }
        return fees.stream().map(EventFeeRequestDto::getLevelId).distinct().count() == fees.size();
    }
}
