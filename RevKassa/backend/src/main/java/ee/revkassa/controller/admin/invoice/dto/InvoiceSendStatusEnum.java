package ee.revkassa.controller.admin.invoice.dto;

public enum InvoiceSendStatusEnum {
    // Invoice saved and emailed
    SENT,
    // Invoice saved with the mail server's error; can be resent with the same number
    FAILED,
    // Nothing saved or sent, see skipReason
    SKIPPED
}
