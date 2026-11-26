package ee.revkassa.service;

import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.membershipcharge.MembershipCharge;
import ee.revkassa.persistence.membershipcharge.MembershipChargeStatusEnum;
import ee.revkassa.persistence.membershipfee.MembershipFee;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * B23: the monthly fee run. Runs for months in 2099, which nobody has been charged for: the run also charges the dev
 * data's members for that month, but everything is rolled back with the test transaction. Asserts only this test's
 * own members and levels.
 */
@SpringBootTest
@Transactional
@Import(TestData.class)
class MembershipChargeServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2099, 10);
    private static final YearMonth NOVEMBER = OCTOBER.plusMonths(1);

    @Autowired
    private MembershipChargeService membershipChargeService;
    @Autowired
    private AccountEntryService accountEntryService;
    @Autowired
    private TestData testData;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ApplicationContext applicationContext;

    private Member admin;
    private MembershipLevel level;

    @BeforeEach
    void createLevelWithFee() {
        admin = member();
        level = level();
        fee(level, YearMonth.of(2099, 1), 500);
    }

    @Test
    void run_chargesTheLevelFeeWithOneLedgerRow() {
        Member member = memberOn(level);

        membershipChargeService.chargeMonth(OCTOBER);

        assertThat(chargesOf(member)).singleElement().satisfies(charge -> {
            assertThat(charge.getMonth()).isEqualTo(OCTOBER.atDay(1));
            assertThat(charge.getLevel().getId()).isEqualTo(level.getId());
            assertThat(charge.getAmountCents()).isEqualTo(500);
            assertThat(charge.getStatus()).isEqualTo(MembershipChargeStatusEnum.CHARGED);
        });
        assertThat(entriesOf(member)).singleElement().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(AccountEntryTypeEnum.MEMBERSHIP_FEE);
            assertThat(entry.getAmountCents()).isEqualTo(-500);
            assertThat(entry.getMembershipCharge().getId()).isEqualTo(chargesOf(member).getFirst().getId());
            assertThat(entry.getCreatedBy()).isNull();
        });
        assertBalance(member, -500);
    }

    @Test
    void secondRun_chargesNobodyTwice() {
        Member member = memberOn(level);
        membershipChargeService.chargeMonth(OCTOBER);

        MembershipChargeService.ChargeRunResult second = membershipChargeService.chargeMonth(OCTOBER);

        assertThat(second.charged()).isZero();
        assertThat(chargesOf(member)).hasSize(1);
        assertThat(entriesOf(member)).hasSize(1);
        assertBalance(member, -500);
    }

    @Test
    void run_differentLevelsPayTheirOwnFees() {
        MembershipLevel junior = level();
        fee(junior, YearMonth.of(2099, 1), 250);
        Member full = memberOn(level);
        Member young = memberOn(junior);

        membershipChargeService.chargeMonth(OCTOBER);

        assertBalance(full, -500);
        assertBalance(young, -250);
        assertThat(chargesOf(young).getFirst().getLevel().getId()).isEqualTo(junior.getId());
    }

    @Test
    void run_skipsExemptAndInactiveMembers() {
        Member exempt = memberOn(level);
        exempt.setFeeExempt(true);
        Member inactive = memberOn(level);
        inactive.setActive(false);
        entityManager.flush();

        membershipChargeService.chargeMonth(OCTOBER);

        assertThat(chargesOf(exempt)).isEmpty();
        assertThat(chargesOf(inactive)).isEmpty();
        assertBalance(exempt, 0);
        assertBalance(inactive, 0);
    }

    @Test
    void memberWhoJoinedInTheMonth_paysFromTheNextMonth() {
        Member joinedAtMidnight = memberOn(level);
        joinedAtMidnight.setCreatedAt(startOf(OCTOBER));
        Member joinedSecondBefore = memberOn(level);
        joinedSecondBefore.setCreatedAt(startOf(OCTOBER).minusSeconds(1));
        entityManager.flush();

        membershipChargeService.chargeMonth(OCTOBER);
        membershipChargeService.chargeMonth(NOVEMBER);

        assertThat(chargesOf(joinedAtMidnight)).extracting(MembershipCharge::getMonth).containsExactly(NOVEMBER.atDay(1));
        assertThat(chargesOf(joinedSecondBefore)).extracting(MembershipCharge::getMonth)
                .containsExactly(OCTOBER.atDay(1), NOVEMBER.atDay(1));
    }

    @Test
    void zeroFeeOrNoFee_writesNothing() {
        MembershipLevel free = level();
        fee(free, YearMonth.of(2099, 1), 0);
        MembershipLevel neverPriced = level();
        Member onFree = memberOn(free);
        Member onNeverPriced = memberOn(neverPriced);

        membershipChargeService.chargeMonth(OCTOBER);

        assertThat(chargesOf(onFree)).isEmpty();
        assertThat(chargesOf(onNeverPriced)).isEmpty();
        assertThat(entriesOf(onFree)).isEmpty();
        assertBalance(onFree, 0);
    }

    @Test
    void lateRun_chargesThatMonthsFee_andARaiseOnlyAddsToLaterMonths() {
        fee(level, NOVEMBER, 700);
        Member member = memberOn(level);

        membershipChargeService.chargeMonth(OCTOBER);
        membershipChargeService.chargeMonth(NOVEMBER);

        assertThat(chargesOf(member)).extracting(MembershipCharge::getAmountCents).containsExactly(500L, 700L);
        assertBalance(member, -1200);
    }

    @Test
    void levelChange_affectsOnlyTheNextCharge() {
        MembershipLevel junior = level();
        fee(junior, YearMonth.of(2099, 1), 250);
        Member member = memberOn(level);
        membershipChargeService.chargeMonth(OCTOBER);

        member.setLevel(junior);
        entityManager.flush();
        membershipChargeService.chargeMonth(NOVEMBER);

        assertThat(chargesOf(member)).extracting(charge -> charge.getLevel().getId(), MembershipCharge::getAmountCents)
                .containsExactly(tuple(level.getId(), 500L),
                        tuple(junior.getId(), 250L));
        assertBalance(member, -750);
    }

    @Test
    void history_showsTheMonthInEstonian() {
        Member member = memberOn(level);

        membershipChargeService.chargeMonth(OCTOBER);
        entityManager.clear();

        assertThat(accountEntryService.getMemberEntries(member.getId(), 0).getEntries())
                .singleElement()
                .satisfies(entry -> assertThat(entry.getDescription()).isEqualTo("Liikmemaks · oktoober 2099"));
    }

    @Test
    void chargeMember_checksTheMemberAgain() {
        Member member = memberOn(level);
        member.setFeeExempt(true);
        entityManager.flush();

        assertThat(membershipChargeService.chargeMember(member.getId(), OCTOBER)).isFalse();
        assertThat(chargesOf(member)).isEmpty();
    }

    @Test
    void chargeMember_alreadyCharged_returnsFalse() {
        Member member = memberOn(level);

        assertThat(membershipChargeService.chargeMember(member.getId(), OCTOBER)).isTrue();
        assertThat(membershipChargeService.chargeMember(member.getId(), OCTOBER)).isFalse();
        assertThat(entriesOf(member)).hasSize(1);
    }

    // Tests share the dev database: a job started with the test context would commit charges to it
    @Test
    void scheduledJob_isOffInTests() {
        assertThat(applicationContext.getBeansOfType(MembershipChargeScheduler.class)).isEmpty();
    }

    // ---- helpers ----

    private Member member() {
        return testData.member("charge-" + UUID.randomUUID() + "@example.org", "charge-hmac-" + UUID.randomUUID());
    }

    private Member memberOn(MembershipLevel memberLevel) {
        Member member = member();
        member.setLevel(memberLevel);
        entityManager.flush();
        return member;
    }

    private MembershipLevel level() {
        MembershipLevel newLevel = new MembershipLevel();
        newLevel.setName("Tasutase " + UUID.randomUUID());
        newLevel.setSortOrder(100);
        newLevel.setActive(true);
        newLevel.setCreatedAt(OffsetDateTime.now());
        entityManager.persist(newLevel);
        return newLevel;
    }

    private void fee(MembershipLevel feeLevel, YearMonth validFrom, long amountCents) {
        MembershipFee fee = new MembershipFee();
        fee.setLevel(feeLevel);
        fee.setValidFrom(validFrom.atDay(1));
        fee.setAmountCents(amountCents);
        fee.setCreatedBy(admin);
        fee.setCreatedAt(OffsetDateTime.now());
        entityManager.persist(fee);
        entityManager.flush();
    }

    private static OffsetDateTime startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ClockConfig.APP_ZONE).toOffsetDateTime();
    }

    private List<MembershipCharge> chargesOf(Member member) {
        return entityManager.createQuery("""
                        select c from MembershipCharge c join fetch c.level where c.member.id = :id order by c.month""",
                        MembershipCharge.class)
                .setParameter("id", member.getId()).getResultList();
    }

    private List<AccountEntry> entriesOf(Member member) {
        return entityManager.createQuery("""
                        select e from AccountEntry e left join fetch e.membershipCharge where e.member.id = :id order by e.id""",
                        AccountEntry.class)
                .setParameter("id", member.getId()).getResultList();
    }

    // balance_cents is changed with one SQL update: read it fresh, and check it against the ledger
    private void assertBalance(Member member, long expectedCents) {
        entityManager.flush();
        entityManager.clear();
        long balance = entityManager.find(Member.class, member.getId()).getBalanceCents();
        long ledgerSum = entityManager.createQuery(
                        "select coalesce(sum(e.amountCents), 0) from AccountEntry e where e.member.id = :id", Long.class)
                .setParameter("id", member.getId()).getSingleResult();
        assertThat(balance).isEqualTo(expectedCents).isEqualTo(ledgerSum);
    }
}
