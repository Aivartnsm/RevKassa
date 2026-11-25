package ee.revkassa.controller.admin.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class WriteOffCreateRequestDto implements Serializable {

    @NotEmpty
    @Size(max = 100)
    private List<@Valid @NotNull WriteOffLineDto> lines;

    // Why: "katki", "aegunud"; required so the ledger explains every loss
    @NotBlank
    @Size(max = 500)
    private String note;
}
