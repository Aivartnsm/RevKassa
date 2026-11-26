package ee.revkassa.service;

import ee.revkassa.controller.admin.membershipfee.dto.ChargeWaiveRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.membershipfee.MembershipFee;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B23: the same member charged from parallel runs with real commits (no test transaction), as when the startup run and
 * the 06:00 run overlap: one charge, one ledger row. B24: the same fee waived by parallel requests: one waiver. Charges only this test's member (never a whole month, which would
 * commit charges for the dev data's members) and removes its own rows afterwards.
 */
@SpringBootTest
@Import(TestData.class)
class MembershipChargeConcurrencyTest {

    private static final YearMonth MONTH = YearMonth.of(2099, 10);
    private static final long FEE_CENTS = 3540;
    private static final int THREADS = 6;

    @Autowired
    private MembershipChargeService membershipChargeService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TestData testData;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long memberId;
    private Long levelId;

    @BeforeEach
    void createMemberOnPricedLevel() {
        transactionTemplate.executeWithoutResult(status -> {
            MembershipLevel level = new MembershipLevel();
            level.setName("Paralleeltase " + UUID.randomUUID());
            level.setSortOrder(100);
            level.setActive(true);
            level.setCreatedAt(OffsetDateTime.now());
            entityManager.persist(level);
            Member member = testData.member("parallel-charge-" + UUID.randomUUID() + "@example.org",
                    "parallel-charge-hmac-" + UUID.randomUUID());
            member.setLevel(level);
            MembershipFee fee = new MembershipFee();
            fee.setLevel(level);
            fee.setValidFrom(MONTH.atDay(1));
            fee.setAmountCents(FEE_CENTS);
            fee.setCreatedBy(member);
            fee.setCreatedAt(OffsetDateTime.now());
            entityManager.persist(fee);
            memberId = member.getId();
            levelId = level.getId();
        });
    }

    @AfterEach
    void removeRows() {
        transactionTemplate.executeWithoutResult(status -> {
            delete("delete from AccountEntry e where e.member.id = :id", memberId);
            delete("delete from MembershipCharge c where c.member.id = :id", memberId);
            delete("delete from MembershipFee f where f.level.id = :id", levelId);
            delete("delete from Member m where m.id = :id", memberId);
            delete("delete from MembershipLevel l where l.id = :id", levelId);
        });
    }

    @Test
    void sameMemberChargedFromParallelRuns_isChargedOnce() throws Exception {
        List<Boolean> results = runAtOnce(() -> membershipChargeService.chargeMember(memberId, MONTH));

        assertThat(results).containsOnlyOnce(true);
        assertThat(count("select count(c) from MembershipCharge c where c.member.id = :id")).isOne();
        assertThat(count("select count(e) from AccountEntry e where e.member.id = :id")).isOne();
        assertThat(memberRepository.findBalanceCents(memberId)).isEqualTo(-FEE_CENTS);
    }

    @Test
    void sameFeeWaivedInParallel_isWaivedOnce() throws Exception {
        membershipChargeService.chargeMember(memberId, MONTH);
        Long chargeId = transactionTemplate.execute(status -> entityManager
                .createQuery("select c.id from MembershipCharge c where c.member.id = :id", Long.class)
                .setParameter("id", memberId).getSingleResult());
        ChargeWaiveRequestDto request = new ChargeWaiveRequestDto();
        request.setNote("Paralleelne tühistus");

        List<Boolean> results = runAtOnce(() -> {
            try {
                membershipChargeService.waiveCharge(chargeId, request, memberId);
                return true;
            } catch (ForbiddenException e) {
                assertThat(e.getError()).isEqualTo(Error.FEE_ALREADY_WAIVED);
                return false;
            }
        });

        assertThat(results).containsOnlyOnce(true);
        assertThat(count("select count(e) from AccountEntry e where e.member.id = :id")).isEqualTo(2);
        assertThat(memberRepository.findBalanceCents(memberId)).isZero();
    }

    private static List<Boolean> runAtOnce(Callable<Boolean> task) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private long count(String jpql) {
        return transactionTemplate.execute(status -> entityManager.createQuery(jpql, Long.class)
                .setParameter("id", memberId).getSingleResult());
    }

    private void delete(String jpql, Long id) {
        entityManager.createQuery(jpql).setParameter("id", id).executeUpdate();
    }
}
