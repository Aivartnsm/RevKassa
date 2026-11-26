package ee.revkassa.controller.admin.invoice.dto;

public enum InvoiceSkipReasonEnum {
    // Balance is 0 or positive
    NOT_IN_DEBT,
    MEMBER_NOT_FOUND,
    // Unexpected error, nothing was saved: sending again is safe
    ERROR
}
