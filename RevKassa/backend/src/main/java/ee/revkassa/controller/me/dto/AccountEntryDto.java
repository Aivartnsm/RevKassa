package ee.revkassa.controller.me.dto;

import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class AccountEntryDto implements Serializable {
    private Long entryId;
    private AccountEntryTypeEnum type;
    // Signed: negative = the member owes more
    private Long amountCents;
    // Ready to show, in Estonian (e.g. "2 × Saku Originaal"); the same text goes into invoices
    private String description;
    private OffsetDateTime createdAt;
    // Only for PURCHASE and PURCHASE_REVERSAL rows
    private Long purchaseId;
}
