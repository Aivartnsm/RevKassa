package ee.revkassa.controller.admin.invoice.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class InvoiceSendResultDto implements Serializable {
    private Long memberId;
    private InvoiceSendStatusEnum status;
    // Only when SKIPPED
    private InvoiceSkipReasonEnum skipReason;
    // The saved invoice when SENT or FAILED (its error is there)
    private InvoiceDto invoice;
}
