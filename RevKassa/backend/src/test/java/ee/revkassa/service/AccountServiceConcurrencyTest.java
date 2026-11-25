package ee.revkassa.service;

import ee.revkassa.persistence.accountentry.AccountEntryRepository;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real commits from parallel threads (no test transaction). The test removes its own rows afterwards.
 */
@SpringBootTest
@Import(TestData.class)
class AccountServiceConcurrencyTest {

    private static final int THREADS = 10;

    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountEntryRepository accountEntryRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TestData testData;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Member member;

    @BeforeEach
    void createMember() {
        member = transactionTemplate.execute(status -> testData.member("ledger-parallel@example.org", "ledger-parallel-pin-hmac"));
    }

    @AfterEach
    void removeRows() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("delete from AccountEntry e where e.member.id = :id").setParameter("id", member.getId()).executeUpdate();
            entityManager.createQuery("delete from Member m where m.id = :id").setParameter("id", member.getId()).executeUpdate();
        });
    }

    @Test
    void parallelPostingsToSameMember_loseNothing() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            results.add(executor.submit(() -> {
                start.await();
                return accountService.post(AccountPosting.adjustment(member, -250, "paralleelne", member));
            }));
        }

        start.countDown();
        for (Future<Long> result : results) {
            result.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        long balance = memberRepository.findBalanceCents(member.getId());
        assertThat(balance).isEqualTo(-250L * THREADS).isEqualTo(accountEntryRepository.sumAmountCents(member.getId()));
    }

    @Test
    void memberFormSavedWhileAPostingCommits_keepsThePostedBalance() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch posted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> form = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            Member inForm = memberRepository.findById(member.getId()).orElseThrow();
            loaded.countDown();
            await(posted);
            inForm.setDisplayName("Muudetud Nimi");
        }));

        loaded.await(30, TimeUnit.SECONDS);
        accountService.post(AccountPosting.adjustment(member, -1250, "ost vormi ajal", member));
        posted.countDown();
        form.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        Member saved = transactionTemplate.execute(status -> memberRepository.findById(member.getId()).orElseThrow());
        assertThat(saved.getDisplayName()).isEqualTo("Muudetud Nimi");
        assertThat(saved.getBalanceCents()).isEqualTo(-1250);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
