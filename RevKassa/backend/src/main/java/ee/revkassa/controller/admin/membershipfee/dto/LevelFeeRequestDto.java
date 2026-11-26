package ee.revkassa.controller.admin.membershipfee.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.io.Serializable;

@Data
public class LevelFeeRequestDto implements Serializable {

    // 10 000 € a month: far above any real fee, catches a mistyped amount
    public static final long MAX_AMOUNT_CENTS = 1_000_000;

    @NotNull
    @Positive
    private Long levelId;

    // 0 = this level is not charged
    @NotNull
    @PositiveOrZero
    @Max(MAX_AMOUNT_CENTS)
    private Long amountCents;
}
