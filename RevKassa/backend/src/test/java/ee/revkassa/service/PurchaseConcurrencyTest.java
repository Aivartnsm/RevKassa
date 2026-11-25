package ee.revkassa.service;

import ee.revkassa.controller.admin.stock.dto.StocktakeCreateRequestDto;
import ee.revkassa.controller.admin.stock.dto.StocktakeLineDto;
import ee.revkassa.controller.purchase.dto.PurchaseCreateRequestDto;
import ee.revkassa.controller.purchase.dto.PurchaseLineRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.security.LoginMethodEnum;
import ee.revkassa.persistence.accountentry.AccountEntryRepository;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.product.ProductRepository;
import ee.revkassa.persistence.stockmovement.StockMovementRepository;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B11/B12: purchases and undos from parallel threads with real commits (no test transaction): nothing is lost, the
 * same request is recorded once, a purchase is undone once and nothing deadlocks with a stocktake. The test removes its own rows afterwards.
 */
@SpringBootTest
@Import(TestData.class)
class PurchaseConcurrencyTest {

    private static final int START_STOCK = 100;

    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private StockService stockService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private AccountEntryRepository accountEntryRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private TestData testData;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Member anna;
    private Member ben;
    private Member admin;
    private Product lager;
    private Product stout;

    @BeforeEach
    void createMembersAndStockedProducts() {
        transactionTemplate.executeWithoutResult(status -> {
            anna = testData.member("parallel-anna@example.org", "parallel-pin-hmac-1");
            ben = testData.member("parallel-ben@example.org", "parallel-pin-hmac-2");
            admin = testData.member("parallel-admin@example.org", "parallel-pin-hmac-3");
            admin.setRole(MemberRoleEnum.ADMIN);
            lager = testData.product("Parallel Lager", 250, 970, true);
            stout = testData.product("Parallel Stout", 400, 971, true);
            stockService.move(StockMove.delivery(lager, START_STOCK, null, null, admin));
            stockService.move(StockMove.delivery(stout, START_STOCK, null, null, admin));
        });
    }

    @AfterEach
    void removeRows() {
        List<Long> memberIds = List.of(anna.getId(), ben.getId(), admin.getId());
        List<Long> productIds = List.of(lager.getId(), stout.getId());
        transactionTemplate.executeWithoutResult(status -> {
            delete("delete from AccountEntry e where e.member.id in :ids", memberIds);
            delete("delete from StockMovement m where m.product.id in :ids", productIds);
            delete("delete from PurchaseLine l where l.purchase.id in (select p.id from Purchase p where p.member.id in :ids)", memberIds);
            delete("delete from Purchase p where p.member.id in :ids", memberIds);
            delete("delete from Product p where p.id in :ids", productIds);
            delete("delete from Member m where m.id in :ids", memberIds);
        });
    }

    @Test
    void twoMembersBuyingTheSameProductsAtOnce_loseNoStockOrBalance() throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> buy(anna, UUID.randomUUID()));
            tasks.add(() -> buy(ben, UUID.randomUUID()));
        }

        runAtOnce(tasks);

        // each purchase: 1 lager + 2 stout = 1050 cents
        assertThat(stockOf(lager)).isEqualTo(START_STOCK - 10);
        assertThat(stockOf(stout)).isEqualTo(START_STOCK - 20);
        assertThat(balanceOf(anna)).isEqualTo(-5 * 1050);
        assertThat(balanceOf(ben)).isEqualTo(-5 * 1050);
        assertInvariants();
    }

    @Test
    void sameMemberBuyingTwiceAtOnce_bothAreRecorded() throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> buy(anna, UUID.randomUUID()));
        }

        runAtOnce(tasks);

        assertThat(purchaseCountOf(anna)).isEqualTo(6);
        assertThat(balanceOf(anna)).isEqualTo(-6 * 1050);
        assertInvariants();
    }

    @Test
    void sameRequestSentManyTimesAtOnce_isRecordedOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> buy(anna, requestId));
        }

        List<Object> results = runAtOnce(tasks);

        assertThat(results).filteredOn(result -> ((PurchaseService.PurchaseResult) result).created()).hasSize(1);
        assertThat(purchaseCountOf(anna)).isEqualTo(1);
        assertThat(balanceOf(anna)).isEqualTo(-1050);
        assertThat(stockOf(lager)).isEqualTo(START_STOCK - 1);
    }

    @Test
    void purchasesAndStocktakesAtOnce_doNotDeadlock() throws Exception {
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> buy(anna, UUID.randomUUID()));
            tasks.add(() -> buy(ben, UUID.randomUUID()));
            tasks.add(this::countBothAtFifty);
        }

        runAtOnce(tasks);

        assertThat(purchaseCountOf(anna) + purchaseCountOf(ben)).isEqualTo(8);
        assertInvariants();
    }

    @Test
    void sameUndoSentManyTimesAtOnce_reversesOnce() throws Exception {
        Long purchaseId = buy(anna, UUID.randomUUID()).purchase().getPurchaseId();
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> undoOrError(purchaseId, anna));
        }

        List<Object> results = runAtOnce(tasks);

        assertThat(results).filteredOn(result -> result == Error.PURCHASE_ALREADY_REVERSED).hasSize(5);
        assertThat(balanceOf(anna)).isZero();
        assertThat(stockOf(lager)).isEqualTo(START_STOCK);
        assertThat(stockOf(stout)).isEqualTo(START_STOCK);
        assertInvariants();
    }

    @Test
    void undosPurchasesAndStocktakesAtOnce_doNotDeadlock() throws Exception {
        List<Long> purchaseIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            purchaseIds.add(buy(anna, UUID.randomUUID()).purchase().getPurchaseId());
        }
        List<Callable<?>> tasks = new ArrayList<>();
        for (Long purchaseId : purchaseIds) {
            tasks.add(() -> purchaseService.undoPurchase(purchaseId, admin.getId(), LoginMethodEnum.PASSWORD));
            tasks.add(() -> buy(anna, UUID.randomUUID()));
            tasks.add(() -> buy(ben, UUID.randomUUID()));
            tasks.add(this::countBothAtFifty);
        }

        runAtOnce(tasks);

        assertThat(balanceOf(anna)).isEqualTo(-4 * 1050);
        assertInvariants();
    }

    private Object undoOrError(Long purchaseId, Member member) {
        try {
            return purchaseService.undoPurchase(purchaseId, member.getId(), LoginMethodEnum.PASSWORD);
        } catch (ForbiddenException e) {
            return e.getError();
        }
    }

    // Lines on purpose in reverse id order: the service must still lock products in id order
    private PurchaseService.PurchaseResult buy(Member member, UUID requestId) {
        PurchaseCreateRequestDto request = new PurchaseCreateRequestDto();
        request.setRequestId(requestId);
        request.setExpectedTotalCents(1050L);
        request.setLines(List.of(line(stout, 2), line(lager, 1)));
        return purchaseService.createPurchase(member.getId(), request);
    }

    private Object countBothAtFifty() {
        StocktakeCreateRequestDto request = new StocktakeCreateRequestDto();
        request.setLines(List.of(count(stout, 50), count(lager, 50)));
        return stockService.recordStocktake(request, admin.getId());
    }

    private static PurchaseLineRequestDto line(Product product, int quantity) {
        PurchaseLineRequestDto line = new PurchaseLineRequestDto();
        line.setProductId(product.getId());
        line.setQuantity(quantity);
        return line;
    }

    private static StocktakeLineDto count(Product product, int countedQty) {
        StocktakeLineDto line = new StocktakeLineDto();
        line.setProductId(product.getId());
        line.setCountedQty(countedQty);
        return line;
    }

    // Starts all tasks at the same moment and fails if any of them failed or did not finish in time
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

    private void assertInvariants() {
        for (Member member : List.of(anna, ben)) {
            assertThat(balanceOf(member)).isEqualTo(accountEntryRepository.sumAmountCents(member.getId()));
        }
        for (Product product : List.of(lager, stout)) {
            assertThat((long) stockOf(product)).isEqualTo(stockMovementRepository.sumQuantityDelta(product.getId()));
        }
    }

    private int stockOf(Product product) {
        return productRepository.findStockQty(product.getId());
    }

    private long balanceOf(Member member) {
        return memberRepository.findBalanceCents(member.getId());
    }

    private long purchaseCountOf(Member member) {
        return transactionTemplate.execute(status -> entityManager
                .createQuery("select count(p) from Purchase p where p.member.id = :id", Long.class)
                .setParameter("id", member.getId()).getSingleResult());
    }

    private void delete(String jpql, List<Long> ids) {
        entityManager.createQuery(jpql).setParameter("ids", ids).executeUpdate();
    }
}
