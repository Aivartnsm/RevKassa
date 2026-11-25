package ee.revkassa.controller.purchase;

import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import ee.revkassa.persistence.stockmovement.StockMovement;
import ee.revkassa.persistence.stockmovement.StockMovementTypeEnum;
import ee.revkassa.service.PurchaseService;
import ee.revkassa.service.StockMove;
import ee.revkassa.service.StockService;
import ee.revkassa.testdata.TestClock;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B12: POST /api/purchases/{id}/undo through the real security chain and PostgreSQL, with a clock the test moves.
 * Parallel undos are in PurchaseConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class PurchaseUndoFlowTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(Instant.parse("2026-11-25T18:00:00Z"));
        }
    }

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
    @Autowired
    private TestClock clock;

    private Member member;
    private MockHttpSession memberSession;
    private Member admin;
    private MockHttpSession adminSession;
    private Product lager;
    private Product stout;

    @BeforeEach
    void createMembersAndProducts() throws Exception {
        member = testSessions.createMember(MemberRoleEnum.MEMBER);
        memberSession = testSessions.passwordSession(member);
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        adminSession = testSessions.passwordSession(admin);
        lager = testData.product("Undo Test Lager", 250, 980, true);
        stout = testData.product("Undo Test Stout", 400, 981, true);
        stockService.move(StockMove.delivery(lager, 10, null, null, admin));
        stockService.move(StockMove.delivery(stout, 10, null, null, admin));
    }

    // ---- what is written ----

    @Test
    void undo_returnsReversedPurchaseWithBalanceBack() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);

        undo(memberSession, purchaseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseId").value(purchaseId))
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.totalCents").value(900))
                .andExpect(jsonPath("$.balanceCents").value(0))
                .andExpect(jsonPath("$.lines.length()").value(2));
    }

    @Test
    void undo_marksPurchaseReversedAndWritesReversalRowsWithoutDeletingAny() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);
        clock.advance(Duration.ofMinutes(3));

        undo(memberSession, purchaseId).andExpect(status().isOk());

        Purchase purchase = entityManager.find(Purchase.class, purchaseId);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatusEnum.REVERSED);
        assertThat(purchase.getReversedAt().toInstant()).isEqualTo(clock.instant());
        assertThat(purchase.getReversedBy().getId()).isEqualTo(member.getId());

        List<AccountEntry> entries = entriesOf(member);
        assertThat(entries).extracting(AccountEntry::getType)
                .containsExactly(AccountEntryTypeEnum.PURCHASE, AccountEntryTypeEnum.PURCHASE_REVERSAL);
        assertThat(entries).extracting(AccountEntry::getAmountCents).containsExactly(-900L, 900L);
        assertThat(entries.get(1).getPurchase().getId()).isEqualTo(purchaseId);
        assertThat(entries.get(1).getCreatedBy().getId()).isEqualTo(member.getId());

        List<StockMovement> movements = movementsOf(purchaseId);
        assertThat(movements).extracting(StockMovement::getType).containsExactly(StockMovementTypeEnum.SALE,
                StockMovementTypeEnum.SALE, StockMovementTypeEnum.SALE_REVERSAL, StockMovementTypeEnum.SALE_REVERSAL);
        assertThat(movements.subList(2, 4)).extracting(movement -> movement.getProduct().getId())
                .containsExactly(lager.getId(), stout.getId());
        assertThat(movements.subList(2, 4)).extracting(StockMovement::getQuantityDelta).containsExactly(2, 1);
    }

    @Test
    void undo_keepsBothInvariants() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);
        buy(memberSession, 250, line(lager, 1));

        undo(memberSession, purchaseId).andExpect(status().isOk());

        assertThat(stockOf(lager)).isEqualTo(9);
        assertThat(stockOf(stout)).isEqualTo(10);
        assertThat(balanceOf(member)).isEqualTo(-250);
        assertThat(entityManager.createNativeQuery("""
                select m.id from member m left join account_entry e on e.member_id = m.id
                group by m.id, m.balance_cents having m.balance_cents <> coalesce(sum(e.amount_cents), 0)""")
                .getResultList()).isEmpty();
        assertThat(entityManager.createNativeQuery("""
                select p.id from product p left join stock_movement m on m.product_id = p.id
                group by p.id, p.stock_qty having p.stock_qty <> coalesce(sum(m.quantity_delta), 0)""")
                .getResultList()).isEmpty();
    }

    // ---- the 10 minutes ----

    @Test
    void undoAtExactlyTenMinutes_isAllowed() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);
        clock.advance(PurchaseService.UNDO_WINDOW);

        undo(memberSession, purchaseId).andExpect(status().isOk());
    }

    @Test
    void undoAfterTenMinutes_returns403AndChangesNothing() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);
        clock.advance(PurchaseService.UNDO_WINDOW.plusSeconds(1));

        undo(memberSession, purchaseId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(129));

        assertNotUndone(purchaseId);
    }

    @Test
    void adminAfterTenMinutes_canUndoAnyonesPurchase() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);
        clock.advance(Duration.ofDays(30));

        undo(adminSession, purchaseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(0));

        assertThat(entityManager.find(Purchase.class, purchaseId).getReversedBy().getId()).isEqualTo(admin.getId());
        assertThat(entriesOf(member).getLast().getCreatedBy().getId()).isEqualTo(admin.getId());
        assertThat(balanceOf(admin)).isZero();
    }

    // ---- twice ----

    @Test
    void undoTwice_returns403AndReversesOnce() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);
        undo(memberSession, purchaseId).andExpect(status().isOk());

        undo(memberSession, purchaseId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(128));

        assertThat(entriesOf(member)).hasSize(2);
        assertThat(movementsOf(purchaseId)).hasSize(4);
        assertThat(balanceOf(member)).isZero();
        assertThat(stockOf(lager)).isEqualTo(10);
    }

    @Test
    void adminUndoingAlreadyReversedPurchase_returns403() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);
        undo(memberSession, purchaseId).andExpect(status().isOk());

        undo(adminSession, purchaseId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(128));
    }

    // ---- who ----

    @Test
    void anotherMember_getsSameAnswerAsForMissingPurchaseAndChangesNothing() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);

        undo(testSessions.memberSession(), purchaseId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101))
                .andExpect(jsonPath("$.purchaseId").doesNotExist());

        assertNotUndone(purchaseId);
    }

    @Test
    void adminWithPinSession_cannotUndoAnotherMembersPurchase() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);

        undo(testSessions.pinSession(MemberRoleEnum.ADMIN), purchaseId).andExpect(status().isNotFound());

        assertNotUndone(purchaseId);
    }

    @Test
    void pinSession_canUndoOwnPurchase() throws Exception {
        MockHttpSession pinSession = testSessions.pinSession(MemberRoleEnum.MEMBER);
        Long purchaseId = buy(pinSession, 250, line(lager, 1));

        undo(pinSession, purchaseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"));
    }

    @Test
    void missingPurchase_returns404() throws Exception {
        undo(memberSession, Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    void withoutSession_returns401() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);

        mockMvc.perform(post("/api/purchases/" + purchaseId + "/undo").with(csrf()))
                .andExpect(status().isUnauthorized());

        assertNotUndone(purchaseId);
    }

    @Test
    void withoutCsrfToken_returns403() throws Exception {
        Long purchaseId = buyLagerAndStout(memberSession);

        mockMvc.perform(post("/api/purchases/" + purchaseId + "/undo").session(memberSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));

        assertNotUndone(purchaseId);
    }

    // ---- helpers ----

    private Long buyLagerAndStout(MockHttpSession session) throws Exception {
        return buy(session, 900, line(lager, 2), line(stout, 1));
    }

    private Long buy(MockHttpSession session, long expectedTotalCents, String... lines) throws Exception {
        String body = mockMvc.perform(post("/api/purchases").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"expectedTotalCents\":" + expectedTotalCents
                                + ",\"lines\":[" + String.join(",", lines) + "]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        forget();
        return Long.valueOf(body.replaceAll(".*\"purchaseId\":(\\d+).*", "$1"));
    }

    private static String line(Product product, int quantity) {
        return "{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}";
    }

    private ResultActions undo(MockHttpSession session, Long purchaseId) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/purchases/" + purchaseId + "/undo").with(csrf()).session(session));
        forget();
        return result;
    }

    // All requests share the test transaction: write and forget, as a commit would between real requests
    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }

    private void assertNotUndone(Long purchaseId) {
        assertThat(entityManager.find(Purchase.class, purchaseId).getStatus()).isEqualTo(PurchaseStatusEnum.COMPLETED);
        assertThat(entriesOf(member)).hasSize(1);
        assertThat(movementsOf(purchaseId)).hasSize(2);
        assertThat(balanceOf(member)).isEqualTo(-900);
    }

    private List<AccountEntry> entriesOf(Member someone) {
        return entityManager.createQuery("""
                        select e from AccountEntry e left join fetch e.purchase join fetch e.createdBy
                        where e.member.id = :id order by e.id""", AccountEntry.class)
                .setParameter("id", someone.getId()).getResultList();
    }

    private List<StockMovement> movementsOf(Long purchaseId) {
        return entityManager.createQuery(
                        "select m from StockMovement m join fetch m.product where m.purchase.id = :id order by m.id", StockMovement.class)
                .setParameter("id", purchaseId).getResultList();
    }

    private int stockOf(Product product) {
        forget();
        return entityManager.find(Product.class, product.getId()).getStockQty();
    }

    private long balanceOf(Member someone) {
        forget();
        return entityManager.find(Member.class, someone.getId()).getBalanceCents();
    }
}
