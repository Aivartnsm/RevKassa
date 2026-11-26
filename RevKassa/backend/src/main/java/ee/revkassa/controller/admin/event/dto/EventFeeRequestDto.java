package ee.revkassa.controller.admin.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;

@Data
public class EventFeeRequestDto implements Serializable {

    // 10 000 € per event: far above any real fee, catches a mistyped amount
    public static final long MAX_AMOUNT_CENTS = 1_000_000;

    @NotNull
    @Positive
    private Long levelId;

    // 0 = free for this level
    @NotNull
    @PositiveOrZero
    @Max(MAX_AMOUNT_CENTS)
    private Long amountCents;
}
