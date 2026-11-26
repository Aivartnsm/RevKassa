package ee.revkassa.controller.admin.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class EventCreateRequestDto implements Serializable {

    @NotBlank
    @Size(max = 100)
    private String name;

    // May be in the past: an event can be recorded afterwards
    @NotNull
    private OffsetDateTime startsAt;

    @Size(max = 100)
    private String location;

    @Size(max = 2000)
    private String description;
}
