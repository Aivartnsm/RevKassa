package ee.revkassa.controller.admin.event.dto;

import ee.revkassa.persistence.eventbudgetline.BudgetCategoryEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class BudgetLineUpdateRequestDto implements Serializable {

    public static final int MAX_DESCRIPTION_LENGTH = 200;
    public static final int MAX_NOTE_LENGTH = 500;
    // 10 000 € per line: far above any real cost, catches a mistyped amount
    public static final long MAX_AMOUNT_CENTS = 1_000_000;

    @NotNull
    private BudgetCategoryEnum category;

    @NotBlank
    @Size(max = MAX_DESCRIPTION_LENGTH)
    private String description;

    @NotNull
    @PositiveOrZero
    @Max(MAX_AMOUNT_CENTS)
    private Long plannedCents;

    // null = not bought yet
    @PositiveOrZero
    @Max(MAX_AMOUNT_CENTS)
    private Long actualCents;

    @Size(max = MAX_NOTE_LENGTH)
    private String note;
}
