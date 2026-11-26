package ee.revkassa.controller.admin.invoice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * The email as it would be sent now. The number is the next free one and is not reserved, so the invoice actually
 * sent may get a later number.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoicePreviewDto implements Serializable {
    private String number;
    private Long amountCents;
    private LocalDate dueDate;
    private String to;
    private String subject;
    private String body;
}
