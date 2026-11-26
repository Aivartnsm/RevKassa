package ee.revkassa.controller.admin.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * The status is not changed here: closing and cancelling have their own endpoints.
 */
@Data
public class EventUpdateRequestDto implements Serializable {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private OffsetDateTime startsAt;

    @Size(max = 100)
    private String location;

    @Size(max = 2000)
    private String description;

    @NotNull
    private Boolean signupOpen;

    // The version the admin loaded; an older one means someone else saved in between (409)
    @NotNull
    private Long version;
}
