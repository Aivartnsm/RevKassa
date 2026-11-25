package ee.revkassa.controller.purchase;

import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import ee.revkassa.persistence.purchaseline.PurchaseLine;
import ee.revkassa.persistence.stockmovement.StockMovement;
import ee.revkassa.persistence.stockmovement.StockMovementTypeEnum;
import ee.revkassa.service.StockMove;
import ee.revkassa.service.StockService;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B11: POST /api/purchases through the real security chain and PostgreSQL. Parallel purchases are in
 * PurchaseConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class PurchaseFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData testData;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private StockService stockService;
    @Autowired
    private EntityManager entityManager;

    private Member member;
    private MockHttpSession memberSession;
    private Product lager;
    private Product stout;

    @BeforeEach
    void createMemberAndProducts() throws Exception {
        member = testSessions.createMember(MemberRoleEnum.MEMBER);
        memberSession = testSessions.passwordSession(member);
        Member admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        lager = testData.product("Buy Test Lager", 250, 960, true);
        stout = testData.product("Buy Test Stout", 400, 961, true);
        stockService.move(StockMove.delivery(lager, 10, null, null, admin));
        stockService.move(StockMove.delivery(stout, 10, null, null, admin));
    }

    // ---- what is written ----

    @Test
    void purchase_returnsPurchaseWithNewBalanceAndUndoDeadline() throws Exception {
        String body = buy(memberSession, UUID.randomUUID(), 900, line(lager, 2), line(stout, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalCents").value(900))
                .andExpect(jsonPath("$.balanceCents").value(-900))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].productId").value(lager.getId()))
                .andExpect(jsonPath("$.lines[0].name").value("Buy Test Lager"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(250))
                .andExpect(jsonPath("$.lines[0].lineTotalCents").value(500))
                .andExpect(jsonPath("$.lines[1].lineTotalCents").value(400))
                .andReturn().getResponse().getContentAsString();

        Purchase purchase = onlyPurchaseOf(member);
        assertThat(body).contains("\"purchaseId\":" + purchase.getId());
        assertThat(purchase.getCreatedAt().plusMinutes(10).toInstant())
                .isEqualTo(OffsetDateTime.parse(body.replaceAll(".*\"undoableUntil\":\"([^\"]+)\".*", "$1")).toInstant());
    }

    @Test
    void purchase_writesPurchaseLinesLedgerRowAndOneSaleMovementPerLine() throws Exception {
        UUID requestId = UUID.randomUUID();

        buy(memberSession, requestId, 900, line(stout, 1), line(lager, 2)).andExpect(status().isCreated());

        Purchase purchase = onlyPurchaseOf(member);
        assertThat(purchase.getTotalCents()).isEqualTo(900);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatusEnum.COMPLETED);
        assertThat(purchase.getRequestId()).isEqualTo(requestId);
        assertThat(purchase.getReversedAt()).isNull();

        List<PurchaseLine> lines = linesOf(purchase);
        assertThat(lines).extracting(line -> line.getProduct().getId()).containsExactly(lager.getId(), stout.getId());
        assertThat(lines).extracting(PurchaseLine::getQuantity).containsExactly(2, 1);
        assertThat(lines).extracting(PurchaseLine::getUnitPriceCents).containsExactly(250L, 400L);

        List<AccountEntry> entries = entriesOf(member);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getType()).isEqualTo(AccountEntryTypeEnum.PURCHASE);
        assertThat(entries.getFirst().getAmountCents()).isEqualTo(-900);
        assertThat(entries.getFirst().getPurchase().getId()).isEqualTo(purchase.getId());
        assertThat(entries.getFirst().getCreatedBy().getId()).isEqualTo(member.getId());

        List<StockMovement> sales = salesOf(purchase);
        assertThat(sales).extracting(movement -> movement.getProduct().getId()).containsExactly(lager.getId(), stout.getId());
        assertThat(sales).extracting(StockMovement::getQuantityDelta).containsExactly(-2, -1);
        assertThat(sales).allSatisfy(sale -> assertThat(sale.getType()).isEqualTo(StockMovementTypeEnum.SALE));
    }

    @Test
    void purchase_keepsBothInvariantsForEveryMemberAndProduct() throws Exception {
        buy(memberSession, UUID.randomUUID(), 900, line(lager, 2), line(stout, 1));
        buy(memberSession, UUID.randomUUID(), 250, line(lager, 1));

        assertThat(stockOf(lager)).isEqualTo(7);
        assertThat(balanceOf(member)).isEqualTo(-1150);
        assertThat(entityManager.createNativeQuery("""
                select m.id from member m left join account_entry e on e.member_id = m.id
                group by m.id, m.balance_cents having m.balance_cents <> coalesce(sum(e.amount_cents), 0)""")
                .getResultList()).isEmpty();
        assertThat(entityManager.createNativeQuery("""
                select p.id from product p left join stock_movement m on m.product_id = p.id
                group by p.id, p.stock_qty having p.stock_qty <> coalesce(sum(m.quantity_delta), 0)""")
                .getResultList()).isEmpty();
    }

    @Test
    void laterPriceChange_doesNotChangeRecordedPurchase() throws Exception {
        buy(memberSession, UUID.randomUUID(), 500, line(lager, 2));
        changePrice(lager, 300);

        Purchase purchase = onlyPurchaseOf(member);
        assertThat(purchase.getTotalCents()).isEqualTo(500);
        assertThat(linesOf(purchase).getFirst().getUnitPriceCents()).isEqualTo(250);
        assertThat(entriesOf(member).getFirst().getAmountCents()).isEqualTo(-500);
    }

    @Test
    void sameProductInTwoLines_isMergedIntoOneLine() throws Exception {
        buy(memberSession, UUID.randomUUID(), 750, line(lager, 1), line(lager, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].quantity").value(3));

        assertThat(salesOf(onlyPurchaseOf(member))).hasSize(1);
    }

    @Test
    void purchase_canTakeStockBelowZeroAndBalanceHasNoLimit() throws Exception {
        buy(memberSession, UUID.randomUUID(), 6000, line(stout, 15)).andExpect(status().isCreated());

        assertThat(stockOf(stout)).isEqualTo(-5);
        assertThat(balanceOf(member)).isEqualTo(-6000);
    }

    // ---- prices changed ----

    @Test
    void expectedTotalDiffers_returns409AndChargesNothing() throws Exception {
        changePrice(lager, 280);

        buy(memberSession, UUID.randomUUID(), 500, line(lager, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(127));

        assertNothingWritten();
    }

    @Test
    void afterPricesChanged_sameBasketWithNewTotal_isRecorded() throws Exception {
        UUID requestId = UUID.randomUUID();
        changePrice(lager, 280);
        buy(memberSession, requestId, 500, line(lager, 2)).andExpect(status().isConflict());

        buy(memberSession, requestId, 560, line(lager, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(280));
    }

    // ---- the same request again ----

    @Test
    void sameRequestIdAgain_returnsRecordedPurchaseWith200AndChargesOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        String first = buy(memberSession, requestId, 900, line(lager, 2), line(stout, 1))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String again = buy(memberSession, requestId, 900, line(lager, 2), line(stout, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(-900))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(again.replaceAll(".*\"purchaseId\":(\\d+).*", "$1"))
                .isEqualTo(first.replaceAll(".*\"purchaseId\":(\\d+).*", "$1"));
        // the first answer's time comes from the clock, the repeat's from the database: both in the same form
        assertThat(again.replaceAll(".*\"createdAt\":\"([^\"]+)\".*", "$1"))
                .isEqualTo(first.replaceAll(".*\"createdAt\":\"([^\"]+)\".*", "$1"))
                .doesNotEndWith("Z");
        assertThat(purchasesOf(member)).hasSize(1);
        assertThat(balanceOf(member)).isEqualTo(-900);
        assertThat(stockOf(lager)).isEqualTo(8);
    }

    @Test
    void requestIdOfAnotherMember_returns403AndShowsNothing() throws Exception {
        UUID requestId = UUID.randomUUID();
        buy(memberSession, requestId, 250, line(lager, 1)).andExpect(status().isCreated());

        buy(testSessions.memberSession(), requestId, 250, line(lager, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(104))
                .andExpect(jsonPath("$.purchaseId").doesNotExist());
    }

    // ---- products ----

    @Test
    void hiddenProduct_returns403AndChargesNothing() throws Exception {
        Product hidden = testData.product("Buy Test Hidden", 300, 962, false);

        buy(memberSession, UUID.randomUUID(), 550, line(lager, 1), line(hidden, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(126));

        assertNothingWritten();
    }

    @Test
    void unknownProduct_returns404AndChargesNothing() throws Exception {
        buy(memberSession, UUID.randomUUID(), 250, line(lager, 1), "{\"productId\":" + Long.MAX_VALUE + ",\"quantity\":1}")
                .andExpect(status().isNotFound());

        assertNothingWritten();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"expectedTotalCents\":250,\"lines\":[LINE]}",
            "{\"requestId\":\"RID\",\"lines\":[LINE]}",
            "{\"requestId\":\"RID\",\"expectedTotalCents\":250,\"lines\":[]}",
            "{\"requestId\":\"RID\",\"expectedTotalCents\":0,\"lines\":[LINE]}",
            "{\"requestId\":\"RID\",\"expectedTotalCents\":250,\"lines\":[{\"productId\":PID,\"quantity\":0}]}",
            "{\"requestId\":\"RID\",\"expectedTotalCents\":250,\"lines\":[{\"productId\":PID,\"quantity\":100}]}",
            "{\"requestId\":\"RID\",\"expectedTotalCents\":250,\"lines\":[{\"quantity\":1}]}",
            "{\"requestId\":\"not-a-uuid\",\"expectedTotalCents\":250,\"lines\":[LINE]}"})
    void invalidRequest_returns400AndChargesNothing(String body) throws Exception {
        String json = body.replace("LINE", line(lager, 1)).replace("RID", UUID.randomUUID().toString())
                .replace("PID", lager.getId().toString());

        mockMvc.perform(post("/api/purchases").with(csrf()).session(memberSession)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());

        assertNothingWritten();
    }

    @Test
    void moreThan50Lines_returns400() throws Exception {
        String[] lines = Collections.nCopies(51, line(lager, 1)).toArray(String[]::new);

        buy(memberSession, UUID.randomUUID(), 51 * 250, lines).andExpect(status().isBadRequest());
    }

    // ---- who ----

    @Test
    void pinSession_canBuy() throws Exception {
        buy(testSessions.pinSession(MemberRoleEnum.MEMBER), UUID.randomUUID(), 250, line(lager, 1))
                .andExpect(status().isCreated());
    }

    @Test
    void withoutSession_returns401() throws Exception {
        mockMvc.perform(post("/api/purchases").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), 250, line(lager, 1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/purchases").session(memberSession).contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), 250, line(lager, 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- helpers ----

    private ResultActions buy(MockHttpSession session, UUID requestId, long expectedTotalCents, String... lines) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/purchases").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body(requestId, expectedTotalCents, lines)));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private static String body(UUID requestId, long expectedTotalCents, String... lines) {
        return "{\"requestId\":\"" + requestId + "\",\"expectedTotalCents\":" + expectedTotalCents
                + ",\"lines\":[" + String.join(",", lines) + "]}";
    }

    private static String line(Product product, int quantity) {
        return "{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}";
    }

    private void changePrice(Product product, long priceCents) throws Exception {
        Product fresh = entityManager.find(Product.class, product.getId());
        mockMvc.perform(put("/api/admin/products/" + product.getId()).with(csrf()).session(testSessions.adminSession())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","priceCents":%d,"lowStockThreshold":6,"sortOrder":%d,"active":true,"version":%d}"""
                                .formatted(fresh.getName(), priceCents, fresh.getSortOrder(), fresh.getVersion())))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    private void assertNothingWritten() {
        assertThat(purchasesOf(member)).isEmpty();
        assertThat(entriesOf(member)).isEmpty();
        assertThat(balanceOf(member)).isZero();
        assertThat(stockOf(lager)).isEqualTo(10);
    }

    private List<Purchase> purchasesOf(Member someone) {
        return entityManager.createQuery("select p from Purchase p where p.member.id = :id order by p.id", Purchase.class)
                .setParameter("id", someone.getId()).getResultList();
    }

    private Purchase onlyPurchaseOf(Member someone) {
        List<Purchase> purchases = purchasesOf(someone);
        assertThat(purchases).hasSize(1);
        return purchases.getFirst();
    }

    private List<PurchaseLine> linesOf(Purchase purchase) {
        return entityManager.createQuery(
                        "select l from PurchaseLine l join fetch l.product where l.purchase.id = :id order by l.id", PurchaseLine.class)
                .setParameter("id", purchase.getId()).getResultList();
    }

    private List<AccountEntry> entriesOf(Member someone) {
        return entityManager.createQuery("""
                        select e from AccountEntry e left join fetch e.purchase join fetch e.createdBy
                        where e.member.id = :id order by e.id""", AccountEntry.class)
                .setParameter("id", someone.getId()).getResultList();
    }

    private List<StockMovement> salesOf(Purchase purchase) {
        return entityManager.createQuery(
                        "select m from StockMovement m join fetch m.product where m.purchase.id = :id order by m.id", StockMovement.class)
                .setParameter("id", purchase.getId()).getResultList();
    }

    private int stockOf(Product product) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Product.class, product.getId()).getStockQty();
    }

    private long balanceOf(Member someone) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Member.class, someone.getId()).getBalanceCents();
    }
}
