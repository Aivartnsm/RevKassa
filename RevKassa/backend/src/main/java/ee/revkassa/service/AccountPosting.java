package ee.revkassa.service;

import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum.AccountEntryLinkEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.membershipcharge.MembershipCharge;
import ee.revkassa.persistence.payment.Payment;
import ee.revkassa.persistence.purchase.Purchase;

import java.util.Map;
import java.util.Objects;

/**
 * One balance change to post with AccountService.post. Only valid combinations can be built: the type fixes the sign
 * of the amount, which link must be present (and that no other is) and whether a note is required. Breaking a rule is
 * a programming error (IllegalArgumentException), not a user error: callers validate user input first.
 * Create it with the method for the type; each task adds its own (fee waiver in B24, event fees in B28, ...).
 *
 * @param createdBy the member or admin who caused it; null only when the app itself posts (monthly fee job)
 */
public record AccountPosting(Member member, AccountEntryTypeEnum type, long amountCents, Purchase purchase,
                             Payment payment, MembershipCharge membershipCharge, Long eventParticipantId, String note,
                             Member createdBy) {

    public AccountPosting {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(type, "type");
        if (!type.getSign().allows(amountCents)) {
            throw new IllegalArgumentException(type + " needs a " + type.getSign() + " amount, got " + amountCents);
        }
        validateLinks(type, Map.of(
                AccountEntryLinkEnum.PURCHASE, purchase != null,
                AccountEntryLinkEnum.PAYMENT, payment != null,
                AccountEntryLinkEnum.MEMBERSHIP_CHARGE, membershipCharge != null,
                AccountEntryLinkEnum.EVENT_PARTICIPANT, eventParticipantId != null));
        note = note == null || note.isBlank() ? null : note.strip();
        if (type.isNoteRequired() && note == null) {
            throw new IllegalArgumentException(type + " needs a note");
        }
    }

    /**
     * The member's own purchase: the balance goes down by the total.
     */
    public static AccountPosting purchase(Member member, long totalCents, Purchase purchase) {
        return new AccountPosting(member, AccountEntryTypeEnum.PURCHASE, -totalCents, purchase, null, null, null, null, member);
    }

    /**
     * Undoing a purchase: the whole total back to the member who bought. createdBy is the member themselves or an admin.
     */
    public static AccountPosting purchaseReversal(Member member, Purchase purchase, Member reversedBy) {
        return new AccountPosting(member, AccountEntryTypeEnum.PURCHASE_REVERSAL, purchase.getTotalCents(), purchase,
                null, null, null, null, reversedBy);
    }

    /**
     * A payment an admin confirmed (or entered as confirmed): its whole amount to the payer's balance.
     */
    public static AccountPosting payment(Payment payment, Member admin) {
        return new AccountPosting(payment.getMember(), AccountEntryTypeEnum.PAYMENT, payment.getAmountCents(), null,
                payment, null, null, null, admin);
    }

    /**
     * An admin's manual correction, either direction (B17). The note says why.
     */
    public static AccountPosting adjustment(Member member, long amountCents, String note, Member admin) {
        return new AccountPosting(member, AccountEntryTypeEnum.ADJUSTMENT, amountCents, null, null, null, null, note, admin);
    }

    /**
     * This month's fee, charged by the app itself (no createdBy): the balance goes down by the charged amount.
     */
    public static AccountPosting membershipFee(MembershipCharge charge) {
        return new AccountPosting(charge.getMember(), AccountEntryTypeEnum.MEMBERSHIP_FEE, -charge.getAmountCents(),
                null, null, charge, null, null, null);
    }

    /**
     * An admin cancels a charged fee: the whole charged amount back. The note says why and is shown to the member.
     */
    public static AccountPosting membershipFeeWaiver(MembershipCharge charge, String note, Member admin) {
        return new AccountPosting(charge.getMember(), AccountEntryTypeEnum.MEMBERSHIP_FEE_WAIVER, charge.getAmountCents(),
                null, null, charge, null, note, admin);
    }

    // Exactly the link the type needs, no other
    private static void validateLinks(AccountEntryTypeEnum type, Map<AccountEntryLinkEnum, Boolean> present) {
        present.forEach((link, isPresent) -> {
            if (isPresent != (link == type.getLink())) {
                throw new IllegalArgumentException(type + (isPresent ? " must not have a " : " needs a ") + link + " link");
            }
        });
    }
}
