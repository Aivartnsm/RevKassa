package ee.revkassa.service;

import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.membershipcharge.MembershipCharge;
import ee.revkassa.persistence.payment.Payment;
import ee.revkassa.persistence.purchase.Purchase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountPostingTest {

    private final Member member = new Member();
    private final Member admin = new Member();

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "PURCHASE, -250", "PURCHASE_REVERSAL, 250", "PAYMENT, 5000", "ADJUSTMENT, -100", "ADJUSTMENT, 100",
            "MEMBERSHIP_FEE, -500", "MEMBERSHIP_FEE_WAIVER, 500", "EVENT_FEE, -1500", "EVENT_FEE_REVERSAL, 1500"})
    void rightSign_withItsLinkAndNote_isAccepted(AccountEntryTypeEnum type, long amountCents) {
        assertThatNoException().isThrownBy(() -> valid(type, amountCents));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "PURCHASE, 250", "PURCHASE_REVERSAL, -250", "PAYMENT, -5000", "MEMBERSHIP_FEE, 500",
            "MEMBERSHIP_FEE_WAIVER, -500", "EVENT_FEE, 1500", "EVENT_FEE_REVERSAL, -1500"})
    void wrongSign_isRefused(AccountEntryTypeEnum type, long amountCents) {
        assertThatThrownBy(() -> valid(type, amountCents))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @ParameterizedTest
    @EnumSource(AccountEntryTypeEnum.class)
    void zeroAmount_isRefusedForEveryType(AccountEntryTypeEnum type) {
        assertThatThrownBy(() -> valid(type, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void purchaseReversal_givesTheWholeTotalBackAndRecordsWhoUndid() {
        Purchase purchase = new Purchase();
        purchase.setTotalCents(900L);

        AccountPosting posting = AccountPosting.purchaseReversal(member, purchase, admin);

        assertThat(posting.type()).isEqualTo(AccountEntryTypeEnum.PURCHASE_REVERSAL);
        assertThat(posting.amountCents()).isEqualTo(900);
        assertThat(posting.purchase()).isSameAs(purchase);
        assertThat(posting.createdBy()).isSameAs(admin);
    }

    @Test
    void payment_creditsThePayerTheWholeAmountAndRecordsTheAdmin() {
        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmountCents(2500L);

        AccountPosting posting = AccountPosting.payment(payment, admin);

        assertThat(posting.type()).isEqualTo(AccountEntryTypeEnum.PAYMENT);
        assertThat(posting.member()).isSameAs(member);
        assertThat(posting.amountCents()).isEqualTo(2500);
        assertThat(posting.payment()).isSameAs(payment);
        assertThat(posting.createdBy()).isSameAs(admin);
    }

    @Test
    void purchaseWithoutPurchaseLink_isRefused() {
        assertThatThrownBy(() -> new AccountPosting(member, AccountEntryTypeEnum.PURCHASE, -250,
                null, null, null, null, null, member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a PURCHASE link");
    }

    @Test
    void paymentWithAnExtraPurchaseLink_isRefused() {
        assertThatThrownBy(() -> new AccountPosting(member, AccountEntryTypeEnum.PAYMENT, 5000,
                new Purchase(), new Payment(), null, null, null, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have a PURCHASE link");
    }

    @Test
    void adjustmentWithBlankNote_isRefused() {
        assertThatThrownBy(() -> AccountPosting.adjustment(member, 100, "  ", admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a note");
    }

    @Test
    void membershipFee_lowersTheBalanceByTheChargedAmountWithoutCreatedBy() {
        MembershipCharge charge = new MembershipCharge();
        charge.setMember(member);
        charge.setAmountCents(3540L);

        AccountPosting posting = AccountPosting.membershipFee(charge);

        assertThat(posting.member()).isSameAs(member);
        assertThat(posting.type()).isEqualTo(AccountEntryTypeEnum.MEMBERSHIP_FEE);
        assertThat(posting.amountCents()).isEqualTo(-3540);
        assertThat(posting.membershipCharge()).isSameAs(charge);
        assertThat(posting.createdBy()).isNull();
    }

    @Test
    void membershipFeeWaiver_givesTheWholeChargedAmountBackWithNoteAndAdmin() {
        MembershipCharge charge = new MembershipCharge();
        charge.setMember(member);
        charge.setAmountCents(3540L);

        AccountPosting posting = AccountPosting.membershipFeeWaiver(charge, "Juhatuse otsus", admin);

        assertThat(posting.type()).isEqualTo(AccountEntryTypeEnum.MEMBERSHIP_FEE_WAIVER);
        assertThat(posting.amountCents()).isEqualTo(3540);
        assertThat(posting.membershipCharge()).isSameAs(charge);
        assertThat(posting.note()).isEqualTo("Juhatuse otsus");
        assertThat(posting.createdBy()).isSameAs(admin);
    }

    @Test
    void waiverWithoutNote_isRefused() {
        assertThatThrownBy(() -> new AccountPosting(member, AccountEntryTypeEnum.MEMBERSHIP_FEE_WAIVER, 500,
                null, null, new MembershipCharge(), null, null, admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adjustment_stripsNote() {
        assertThat(AccountPosting.adjustment(member, -100, "  sularaha vahe  ", admin).note()).isEqualTo("sularaha vahe");
    }

    @Test
    void missingMember_isRefused() {
        assertThatThrownBy(() -> AccountPosting.adjustment(null, 100, "x", admin)).isInstanceOf(NullPointerException.class);
    }

    // A posting with the link the type needs and a note
    private AccountPosting valid(AccountEntryTypeEnum type, long amountCents) {
        Long id = 1L;
        return switch (type.getLink()) {
            case NONE -> new AccountPosting(member, type, amountCents, null, null, null, null, "märkus", admin);
            case PURCHASE -> new AccountPosting(member, type, amountCents, new Purchase(), null, null, null, "märkus", member);
            case PAYMENT -> new AccountPosting(member, type, amountCents, null, new Payment(), null, null, "märkus", admin);
            case MEMBERSHIP_CHARGE -> new AccountPosting(member, type, amountCents, null, null, new MembershipCharge(), null, "märkus", null);
            case EVENT_PARTICIPANT -> new AccountPosting(member, type, amountCents, null, null, null, id, "märkus", admin);
        };
    }
}
