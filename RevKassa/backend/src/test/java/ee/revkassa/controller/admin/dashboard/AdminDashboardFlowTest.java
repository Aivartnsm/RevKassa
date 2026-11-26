package ee.revkassa.controller.admin.dashboard;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.payment.Payment;
import ee.revkassa.persistence.payment.PaymentMethodEnum;
import ee.revkassa.persistence.payment.PaymentStatusEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import ee.revkassa.persistence.purchaseline.PurchaseLine;
import ee.revkassa.service.AccountPosting;
import ee.revkassa.service.AccountService;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B18: the admin overview through the real security chain and PostgreSQL. The overview counts the whole database,
 * so each test reads the figures before and after adding its own rows and checks the difference; list tests use
 * amounts far beyond the dev data so this test's rows come first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminDashboardFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private TestData testData;
    @Autowired
    private AccountService accountService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private Member admin;
    private MockHttpSession adminSession;

    @BeforeEach
    void createAdmin() throws Exception {
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        adminSession = testSessions.passwordSession(admin);
        forget();
    }

    // ---- debts ----

    @Test
    void openDebts_addUpAllNegativeBalancesIncludingInactiveMembers() throws Exception {
        String before = dashboard();

        memberWithBalance("Võlgnik Üks", -1250, true);
        memberWithBalance("Endine Võlgnik", -2000, false);
        memberWithBalance("Ettemaksja", 5000, true);
        String after = dashboard();

        assertThat(number(after, "$.openDebtCents")).isEqualTo(number(before, "$.openDebtCents") + 3250);
        assertThat(number(after, "$.debtorCount")).isEqualTo(number(before, "$.debtorCount") + 2);
    }

    @Test
    void largestDebts_showTheFiveBiggestFirstWithFormerMembersMarked() throws Exception {
        Member biggest = memberWithBalance("Suurim Võlg", -9_000_003, true);
        Member former = memberWithBalance("Endine Liige", -9_000_002, false);
        for (int i = 0; i < 4; i++) {
            memberWithBalance("Võlgnik " + i, -9_000_001, true);
        }

        send()
                .andExpect(jsonPath("$.largestDebts.length()").value(5))
                .andExpect(jsonPath("$.largestDebts[0].memberId").value(Math.toIntExact(biggest.getId())))
                .andExpect(jsonPath("$.largestDebts[0].name").value("Suurim Võlg"))
                .andExpect(jsonPath("$.largestDebts[0].owedCents").value(9_000_003))
                .andExpect(jsonPath("$.largestDebts[0].active").value(true))
                .andExpect(jsonPath("$.largestDebts[1].memberId").value(Math.toIntExact(former.getId())))
                .andExpect(jsonPath("$.largestDebts[1].active").value(false));
    }

    // ---- payments ----

    @Test
    void pendingPayments_countOnlyPendingOnes() throws Exception {
        Member payer = memberWithBalance("Maksja", 0, true);
        String before = dashboard();

        payment(payer, 2500, PaymentStatusEnum.PENDING);
        payment(payer, 1340, PaymentStatusEnum.PENDING);
        payment(payer, 9999, PaymentStatusEnum.REJECTED);
        String after = dashboard();

        assertThat(number(after, "$.pendingPaymentCount")).isEqualTo(number(before, "$.pendingPaymentCount") + 2);
        assertThat(number(after, "$.pendingPaymentCents")).isEqualTo(number(before, "$.pendingPaymentCents") + 3840);
    }

    // ---- stock ----

    @Test
    void stockToCheck_listsLowAndNegativeProductsForSaleNegativeFirst() throws Exception {
        String before = dashboard();

        Product low = product("Vähene", 2, 6, true);
        Product atLimit = product("Piiril", 6, 6, true);
        Product empty = product("Tühi", 0, 6, true);
        Product negative = product("Miinuses", -900_000, 6, true);
        Product enough = product("Piisav", 7, 6, true);
        Product hidden = product("Peidetud", -900_001, 6, false);
        String after = dashboard();

        assertThat(number(after, "$.lowStockCount")).isEqualTo(number(before, "$.lowStockCount") + 3);
        assertThat(number(after, "$.negativeStockCount")).isEqualTo(number(before, "$.negativeStockCount") + 1);
        List<Integer> listed = JsonPath.read(after, "$.stockToCheck[*].productId");
        assertThat(listed.getFirst()).isEqualTo(Math.toIntExact(negative.getId()));
        assertThat(listed).contains(id(low), id(atLimit), id(empty)).doesNotContain(id(enough), id(hidden));
        assertThat(listed.indexOf(id(empty))).isLessThan(listed.indexOf(id(low)));
        assertThat(listed.indexOf(id(low))).isLessThan(listed.indexOf(id(atLimit)));
    }

    @Test
    void stockToCheck_givesStatusAndWarningLevel() throws Exception {
        Product negative = product("Miinuses", -900_000, 6, true);
        Product low = product("Vähene", 1, 4, true);

        send()
                .andExpect(jsonPath("$.stockToCheck[0].productId").value(id(negative)))
                .andExpect(jsonPath("$.stockToCheck[0].stockQty").value(-900_000))
                .andExpect(jsonPath("$.stockToCheck[0].status").value("NEGATIVE"))
                .andExpect(jsonPath("$.stockToCheck[?(@.productId == " + low.getId() + ")].status").value("LOW"))
                .andExpect(jsonPath("$.stockToCheck[?(@.productId == " + low.getId() + ")].lowStockThreshold").value(4));
    }

    // ---- this month's best sellers ----

    @Test
    void topProducts_rankThisMonthsUnitsAndSkipUndonePurchases() throws Exception {
        Member buyer = memberWithBalance("Ostja", 0, true);
        Product first = product("Enim müüdud", 50, 6, true);
        Product second = product("Teisena", 50, 6, true);
        Product undone = product("Tagasi võetud", 50, 6, true);
        OffsetDateTime thisMonth = startOf(YearMonth.now(clock));
        purchase(buyer, thisMonth, PurchaseStatusEnum.COMPLETED, first, 600_000);
        purchase(buyer, thisMonth.plusDays(1), PurchaseStatusEnum.COMPLETED, first, 400_000);
        purchase(buyer, thisMonth, PurchaseStatusEnum.COMPLETED, second, 900_000);
        purchase(buyer, thisMonth, PurchaseStatusEnum.REVERSED, undone, 5_000_000);

        send()
                .andExpect(jsonPath("$.month").value(YearMonth.now(clock).toString()))
                .andExpect(jsonPath("$.topProducts[0].productId").value(id(first)))
                .andExpect(jsonPath("$.topProducts[0].name").value(first.getName()))
                .andExpect(jsonPath("$.topProducts[0].quantity").value(1_000_000))
                .andExpect(jsonPath("$.topProducts[1].productId").value(id(second)))
                .andExpect(jsonPath("$.topProducts[?(@.productId == " + undone.getId() + ")]").isEmpty());
    }

    @Test
    void topProducts_countOnlyThisMonthInClubTime() throws Exception {
        Member buyer = memberWithBalance("Ostja", 0, true);
        Product lastMonthOnly = product("Eelmise kuu", 50, 6, true);
        Product fromMidnight = product("Keskööst", 50, 6, true);
        OffsetDateTime thisMonth = startOf(YearMonth.now(clock));
        purchase(buyer, thisMonth.minusSeconds(1), PurchaseStatusEnum.COMPLETED, lastMonthOnly, 9_000_000);
        purchase(buyer, thisMonth, PurchaseStatusEnum.COMPLETED, fromMidnight, 8_000_000);

        send()
                .andExpect(jsonPath("$.topProducts[0].productId").value(id(fromMidnight)))
                .andExpect(jsonPath("$.topProducts[?(@.productId == " + lastMonthOnly.getId() + ")]").isEmpty());
    }

    @Test
    void topProducts_holdAtMostFive() throws Exception {
        Member buyer = memberWithBalance("Ostja", 0, true);
        for (int i = 0; i < 6; i++) {
            purchase(buyer, startOf(YearMonth.now(clock)), PurchaseStatusEnum.COMPLETED,
                    product("Toode " + i, 50, 6, true), 1_000_000 + i);
        }

        send().andExpect(jsonPath("$.topProducts.length()").value(5));
    }

    // ---- access ----

    @Test
    void byMember_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(csrf()).session(testSessions.memberSession()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void byAdminWithPin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(csrf()).session(testSessions.pinSession(MemberRoleEnum.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(115));
    }

    // ---- helpers ----

    private ResultActions send() throws Exception {
        forget();
        return mockMvc.perform(get("/api/admin/dashboard").with(csrf()).session(adminSession))
                .andExpect(status().isOk());
    }

    private String dashboard() throws Exception {
        return send().andReturn().getResponse().getContentAsString();
    }

    private static long number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private static int id(Product product) {
        return Math.toIntExact(product.getId());
    }

    // The balance through the ledger, as in the app: balance = sum of the member's rows
    private Member memberWithBalance(String name, long balanceCents, boolean active) {
        Member member = testData.member("dashboard-" + UUID.randomUUID() + "@example.org", "dashboard-hmac-" + UUID.randomUUID());
        member.setDisplayName(name);
        member.setActive(active);
        if (balanceCents != 0) {
            accountService.post(AccountPosting.adjustment(member, balanceCents, "testi saldo", admin));
        }
        entityManager.flush();
        return member;
    }

    private void payment(Member payer, long amountCents, PaymentStatusEnum status) {
        Payment payment = new Payment();
        payment.setMember(payer);
        payment.setAmountCents(amountCents);
        payment.setMethod(PaymentMethodEnum.BANK_TRANSFER);
        payment.setStatus(status);
        payment.setReportedAt(OffsetDateTime.now());
        if (status != PaymentStatusEnum.PENDING) {
            payment.setDecidedBy(entityManager.find(Member.class, admin.getId()));
            payment.setDecidedAt(OffsetDateTime.now());
        }
        entityManager.persist(payment);
        entityManager.flush();
    }

    // Stock set directly: only the overview's reading of it is tested here
    private Product product(String name, int stockQty, int lowStockThreshold, boolean active) {
        Product product = testData.product(name + " " + UUID.randomUUID(), 350, 1, active);
        product.setStockQty(stockQty);
        product.setLowStockThreshold(lowStockThreshold);
        entityManager.flush();
        return product;
    }

    // Lines only, without ledger or stock rows: the overview reads purchase lines
    private void purchase(Member buyer, OffsetDateTime createdAt, PurchaseStatusEnum status, Product product, int quantity) {
        Purchase purchase = new Purchase();
        purchase.setMember(entityManager.find(Member.class, buyer.getId()));
        purchase.setTotalCents((long) quantity * product.getPriceCents());
        purchase.setStatus(status);
        purchase.setCreatedAt(createdAt);
        entityManager.persist(purchase);
        PurchaseLine line = new PurchaseLine();
        line.setPurchase(purchase);
        line.setProduct(entityManager.find(Product.class, product.getId()));
        line.setQuantity(quantity);
        line.setUnitPriceCents(product.getPriceCents());
        entityManager.persist(line);
        entityManager.flush();
    }

    private static OffsetDateTime startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ClockConfig.APP_ZONE).toOffsetDateTime();
    }

    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
