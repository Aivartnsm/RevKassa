package ee.revkassa.controller.admin.invoice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class InvoiceCreateRequestDto implements Serializable {

    // Emails go out one by one within the request: the limit keeps one request reasonably short
    public static final int MAX_MEMBERS = 200;

    @NotEmpty
    @Size(max = MAX_MEMBERS)
    private List<@NotNull Long> memberIds;
}
