package ee.revkassa.controller.admin.invoice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePageDto implements Serializable {
    // Newest first
    private List<InvoiceDto> invoices;
    private int page;
    // true: "Laadi veel" asks for page + 1
    private boolean hasMore;
}
