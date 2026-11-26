package ee.revkassa.controller.admin.membershiplevel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * active = false hides the level from new choices; its members keep it.
 */
@Data
public class MembershipLevelUpdateRequestDto implements Serializable {

    @NotBlank
    @Size(max = MembershipLevelCreateRequestDto.MAX_NAME_LENGTH)
    private String name;

    @NotNull
    @PositiveOrZero
    @Max(10_000)
    private Integer sortOrder;

    @NotNull
    private Boolean active;
}
