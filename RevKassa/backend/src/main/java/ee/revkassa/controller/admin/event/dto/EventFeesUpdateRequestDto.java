package ee.revkassa.controller.admin.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * The whole fee table of an event: a level that is not listed pays 0. Participants added earlier keep their fee.
 */
@Data
public class EventFeesUpdateRequestDto implements Serializable {

    public static final int MAX_LEVELS = 100;

    // May be empty: the event is free for everyone
    @NotNull
    @Size(max = MAX_LEVELS)
    private List<@NotNull @Valid EventFeeRequestDto> fees;

    // The event version the admin loaded; an older one means someone else saved in between (409)
    @NotNull
    private Long version;

    // Reported as field "levelIdsUnique" in ApiError.fieldErrors
    @AssertTrue(message = "a level is listed more than once")
    @SuppressWarnings("unused") // called by Bean Validation
    private boolean isLevelIdsUnique() {
        if (fees == null || fees.stream().anyMatch(fee -> fee == null || fee.getLevelId() == null)) {
            return true;
        }
        return fees.stream().map(EventFeeRequestDto::getLevelId).distinct().count() == fees.size();
    }
}
