package ee.revkassa.controller.admin.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Counted quantities; products that were not counted are left out (the frontend skips empty rows).
 */
@Data
public class StocktakeCreateRequestDto implements Serializable {

    @NotEmpty
    @Size(max = 200)
    private List<@Valid @NotNull StocktakeLineDto> lines;

    @Size(max = 500)
    private String note;
}
