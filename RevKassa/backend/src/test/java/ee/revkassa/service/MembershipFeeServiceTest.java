package ee.revkassa.service;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.membershipfee.MembershipFee;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B22: feeFor(level, month), which the monthly fee job (B23) charges: the level's latest row starting in or before
 * the month, 0 if there is none. A later change never affects an earlier month.
 */
@SpringBootTest
@Transactional
@Import(TestData.class)
class MembershipFeeServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    private MembershipFeeService membershipFeeService;
    @Autowired
    private TestData testData;
    @Autowired
    private EntityManager entityManager;

    private Member admin;
    private MembershipLevel level;

    @BeforeEach
    void createLevel() {
        admin = testData.member("fee-admin-" + UUID.randomUUID() + "@example.org", "fee-hmac-" + UUID.randomUUID());
        level = level();
    }

    @Test
    void levelWithoutFees_isFree() {
        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER)).isZero();
    }

    @Test
    void feeFor_usesTheLatestRowStartedByThatMonth() {
        fee(level, SEPTEMBER, 500);
        fee(level, SEPTEMBER.plusMonths(2), 700);

        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER.minusMonths(1))).isZero();
        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER)).isEqualTo(500);
        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER.plusMonths(1))).isEqualTo(500);
        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER.plusMonths(2))).isEqualTo(700);
        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER.plusMonths(14))).isEqualTo(700);
    }

    @Test
    void feeFor_zeroRow_makesTheLevelFreeFromThen() {
        fee(level, SEPTEMBER, 500);
        fee(level, SEPTEMBER.plusMonths(1), 0);

        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER)).isEqualTo(500);
        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER.plusMonths(1))).isZero();
    }

    @Test
    void feeFor_looksOnlyAtThatLevel() {
        MembershipLevel otherLevel = level();
        fee(otherLevel, SEPTEMBER, 900);
        fee(level, SEPTEMBER, 250);

        assertThat(membershipFeeService.feeFor(level.getId(), SEPTEMBER)).isEqualTo(250);
        assertThat(membershipFeeService.feeFor(otherLevel.getId(), SEPTEMBER)).isEqualTo(900);
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
}
