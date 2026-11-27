package ee.revkassa.controller.admin.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * The note goes with the balance row written when the fee was already charged; the member sees it in their history
 * ("Ürituse tasu · Jõulupidu · <note>").
 */
@Data
public class ParticipantFeeUpdateRequestDto implements Serializable {

    public static final int MAX_NOTE_LENGTH = 500;

    @NotNull
    @PositiveOrZero
    @Max(EventFeeRequestDto.MAX_AMOUNT_CENTS)
    private Long feeCents;

    @Size(max = MAX_NOTE_LENGTH)
    private String note;
}
