package ee.revkassa.persistence.accountentry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Each type fixes the sign of the amount (from the member's point of view: negative = owes more), which link the row
 * must carry and whether a note is required. AccountPosting checks these before anything is written.
 */
@Getter
@RequiredArgsConstructor
public enum AccountEntryTypeEnum {
    PURCHASE(AmountSignEnum.NEGATIVE, AccountEntryLinkEnum.PURCHASE, false),
    PURCHASE_REVERSAL(AmountSignEnum.POSITIVE, AccountEntryLinkEnum.PURCHASE, false),
    PAYMENT(AmountSignEnum.POSITIVE, AccountEntryLinkEnum.PAYMENT, false),
    ADJUSTMENT(AmountSignEnum.ANY, AccountEntryLinkEnum.NONE, true),
    MEMBERSHIP_FEE(AmountSignEnum.NEGATIVE, AccountEntryLinkEnum.MEMBERSHIP_CHARGE, false),
    MEMBERSHIP_FEE_WAIVER(AmountSignEnum.POSITIVE, AccountEntryLinkEnum.MEMBERSHIP_CHARGE, true),
    EVENT_FEE(AmountSignEnum.NEGATIVE, AccountEntryLinkEnum.EVENT_PARTICIPANT, false),
    EVENT_FEE_REVERSAL(AmountSignEnum.POSITIVE, AccountEntryLinkEnum.EVENT_PARTICIPANT, false);

    private final AmountSignEnum sign;
    private final AccountEntryLinkEnum link;
    private final boolean noteRequired;

    public enum AmountSignEnum {
        NEGATIVE, POSITIVE, ANY;

        public boolean allows(long amountCents) {
            return switch (this) {
                case NEGATIVE -> amountCents < 0;
                case POSITIVE -> amountCents > 0;
                case ANY -> amountCents != 0;
            };
        }
    }

    public enum AccountEntryLinkEnum {
        NONE, PURCHASE, PAYMENT, MEMBERSHIP_CHARGE, EVENT_PARTICIPANT
    }
}
