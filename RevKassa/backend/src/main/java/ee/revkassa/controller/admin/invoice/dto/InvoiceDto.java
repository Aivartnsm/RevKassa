package ee.revkassa.controller.admin.invoice.dto;

import ee.revkassa.persistence.invoice.InvoiceStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
public class InvoiceDto implements Serializable {
    private Long invoiceId;
    private String number;
    private Long memberId;
    private String memberName;
    private String emailTo;
    // The debt when the invoice was made
    private Long amountCents;
    private LocalDate dueDate;
    private InvoiceStatusEnum status;
    // Mail server's reason, only when FAILED
    private String error;
    private OffsetDateTime createdAt;
    // Last successful send, null while FAILED
    private OffsetDateTime sentAt;
    private String sentByName;
}
