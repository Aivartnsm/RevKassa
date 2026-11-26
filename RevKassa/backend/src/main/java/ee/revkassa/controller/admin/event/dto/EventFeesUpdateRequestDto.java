package ee.revkassa.controller.admin.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * The whole fee table of an event: price periods, each with a fee per level. Before the first dated period the
 * period without a date applies; with none, joining before the first date is free. Participants who already joined
 * keep the fee they got.
 */
@Data
public class EventFeesUpdateRequestDto implements Serializable {

    public static final int MAX_PERIODS = 10;

    // May be empty: the event is free for everyone
    @NotNull
    @Size(max = MAX_PERIODS)
    private List<@NotNull @Valid EventFeePeriodRequestDto> periods;

    // The event version the admin loaded; an older one means someone else saved in between (409)
    @NotNull
    private Long version;

    // Reported as field "periodStartsUnique" in ApiError.fieldErrors; at most one period without a date
    @AssertTrue(message = "two periods start on the same day")
    @SuppressWarnings("unused") // called by Bean Validation
    private boolean isPeriodStartsUnique() {
        if (periods == null || periods.stream().anyMatch(Objects::isNull)) {
            return true;
        }
        return periods.stream().map(EventFeePeriodRequestDto::getValidFrom).distinct().count() == periods.size();
    }
}
