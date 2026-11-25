package ee.revkassa.service;

import ee.revkassa.controller.payment.dto.PaymentCreateRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.persistence.accountentry.AccountEntryRepository;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.payment.PaymentRepository;
import ee.revkassa.persistence.payment.PaymentStatusEnum;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B16: decisions on the same payment from parallel threads with real commits (no test transaction): a payment is
 * confirmed once, with one ledger row, and never both confirmed and rejected. The test removes its own rows afterwards.
 */
@SpringBootTest
@Import(TestData.class)
class PaymentConcurrencyTest {

    private static final long AMOUNT_CENTS = 2500;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AccountEntryRepository accountEntryRepository;
    @Autowired
    private TestData testData;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Member payer;
    private Member admin;

    @BeforeEach
    void createMembers() {
        transactionTemplate.executeWithoutResult(status -> {
            payer = testData.member("parallel-payer@example.org", "parallel-payment-pin-hmac-1");
            admin = testData.member("parallel-payment-admin@example.org", "parallel-payment-pin-hmac-2");
            admin.setRole(MemberRoleEnum.ADMIN);
        });
    }

    @AfterEach
    void removeRows() {
        List<Long> memberIds = List.of(payer.getId(), admin.getId());
        transactionTemplate.executeWithoutResult(status -> {
            delete("delete from AccountEntry e where e.member.id in :ids", memberIds);
            delete("delete from Payment p where p.member.id in :ids", memberIds);
            delete("delete from Member m where m.id in :ids", memberIds);
        });
    }

    @Test
    void sameConfirmSentManyTimesAtOnce_confirmsOnceWithOneRow() throws Exception {
        Long paymentId = report();
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> decideOrError(() -> paymentService.confirmPayment(paymentId, admin.getId())));
        }

        List<Object> results = runAtOnce(tasks);

        assertThat(results).filteredOn(result -> result == Error.PAYMENT_ALREADY_DECIDED).hasSize(5);
        assertThat(balanceOf(payer)).isEqualTo(AMOUNT_CENTS).isEqualTo(accountEntryRepository.sumAmountCents(payer.getId()));
        assertThat(ledgerRowCountOf(paymentId)).isOne();
    }

    @Test
    void confirmAndRejectAtOnce_onlyOneWins() throws Exception {
        Long paymentId = report();
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tasks.add(() -> decideOrError(() -> paymentService.confirmPayment(paymentId, admin.getId())));
            tasks.add(() -> decideOrError(() -> paymentService.rejectPayment(paymentId, null, admin.getId())));
        }

        List<Object> results = runAtOnce(tasks);

        assertThat(results).filteredOn(result -> result == Error.PAYMENT_ALREADY_DECIDED).hasSize(5);
        boolean confirmed = statusOf(paymentId) == PaymentStatusEnum.CONFIRMED;
        assertThat(ledgerRowCountOf(paymentId)).isEqualTo(confirmed ? 1 : 0);
        assertThat(balanceOf(payer)).isEqualTo(confirmed ? AMOUNT_CENTS : 0)
                .isEqualTo(accountEntryRepository.sumAmountCents(payer.getId()));
    }

    private Long report() {
        PaymentCreateRequestDto request = new PaymentCreateRequestDto();
        request.setAmountCents(AMOUNT_CENTS);
        return paymentService.reportPayment(payer.getId(), request).getPaymentId();
    }

    private static Object decideOrError(Callable<?> decision) throws Exception {
        try {
            return decision.call();
        } catch (ForbiddenException e) {
            return e.getError();
        }
    }

    private static List<Object> runAtOnce(List<Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }

    private long balanceOf(Member member) {
        return memberRepository.findBalanceCents(member.getId());
    }

    private PaymentStatusEnum statusOf(Long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow().getStatus();
    }

    private long ledgerRowCountOf(Long paymentId) {
        return transactionTemplate.execute(status -> entityManager
                .createQuery("select count(e) from AccountEntry e where e.payment.id = :id", Long.class)
                .setParameter("id", paymentId).getSingleResult());
    }

    private void delete(String jpql, List<Long> ids) {
        entityManager.createQuery(jpql).setParameter("ids", ids).executeUpdate();
    }
}
