package ee.revkassa.controller.admin.event.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * A member listed twice is added once.
 */
@Data
public class ParticipantsAddRequestDto implements Serializable {

    public static final int MAX_MEMBERS = 200;

    @NotEmpty
    @Size(max = MAX_MEMBERS)
    private List<@NotNull @Positive Long> memberIds;
}
