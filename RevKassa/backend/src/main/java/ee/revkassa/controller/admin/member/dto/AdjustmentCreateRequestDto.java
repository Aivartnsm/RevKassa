package ee.revkassa.controller.admin.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * A manual balance correction: positive raises the balance, negative lowers it. The note says why and is shown to the
 * member in their history ("Parandus · <note>").
 */
@Data
public class AdjustmentCreateRequestDto implements Serializable {

    // 100 000 € either way: far above any real correction, catches a mistyped amount
    public static final long MAX_AMOUNT_CENTS = 10_000_000;
    public static final int MAX_NOTE_LENGTH = 500;

    @NotNull
    @Min(-MAX_AMOUNT_CENTS)
    @Max(MAX_AMOUNT_CENTS)
    private Long amountCents;

    @NotBlank
    @Size(max = MAX_NOTE_LENGTH)
    private String note;

    // Reported as field "amountCentsNotZero" in ApiError.fieldErrors
    @AssertTrue(message = "must not be 0")
    private boolean isAmountCentsNotZero() {
        return amountCents == null || amountCents != 0;
    }
}
