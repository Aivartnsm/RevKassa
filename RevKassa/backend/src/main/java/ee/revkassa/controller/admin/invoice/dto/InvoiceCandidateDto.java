package ee.revkassa.controller.admin.invoice.dto;

import ee.revkassa.persistence.invoice.InvoiceStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class InvoiceCandidateDto implements Serializable {
    private Long memberId;
    private String displayName;
    private String email;
    // A deactivated member's debt is still owed
    private Boolean active;
    // Negative = the debt
    private Long balanceCents;
    // The newest invoice, whatever its status; null if never invoiced
    private OffsetDateTime lastInvoiceAt;
    private InvoiceStatusEnum lastInvoiceStatus;
    // Ticked by default unless an invoice reached the member in the last 7 days; the admin may still send
    private Boolean defaultSelected;
}
