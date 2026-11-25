package ee.revkassa.controller.admin.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * One delivery: all lines are saved together or not at all.
 */
@Data
public class DeliveryCreateRequestDto implements Serializable {

    @NotEmpty
    @Size(max = 100)
    private List<@Valid @NotNull DeliveryLineDto> lines;

    @Size(max = 500)
    private String note;
}
