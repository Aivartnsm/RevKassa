package ee.revkassa.controller.admin.report;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import ee.revkassa.persistence.purchaseline.PurchaseLine;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B19: the sales report through the real security chain and PostgreSQL. Purchases are written in 2099, so a range in
 * 2099 holds only this test's sales and the totals are exact. (The dev data's active products also show up as
 * products that sold nothing, with 0.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminSalesReportFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private TestData testData;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private MockHttpSession adminSession;
    private Member buyer;
    private Product lager;
    private Product stout;

    @BeforeEach
    void createProducts() throws Exception {
        adminSession = testSessions.passwordSession(testSessions.createMember(MemberRoleEnum.ADMIN));
        buyer = testSessions.createMember(MemberRoleEnum.MEMBER);
        lager = product("Aruande Lager", 250, 110L, true);
        stout = product("Aruande Stout", 450, 240L, true);
        forget();
    }

    // ---- units, revenue, order ----

    @Test
    void report_ranksProductsByUnitsWithRevenueAndMonthlyUnits() throws Exception {
        sale(at(2099, 10, 5, 20, 0), lager, 3, 250, 110L);
        sale(at(2099, 11, 2, 20, 0), lager, 2, 300, 110L);
        sale(at(2099, 11, 3, 20, 0), stout, 4, 450, 240L);

        report("2099-10-01", "2099-11-30")
                .andExpect(jsonPath("$.from").value("2099-10-01"))
                .andExpect(jsonPath("$.to").value("2099-11-30"))
                .andExpect(jsonPath("$.months", contains("2099-10", "2099-11")))
                .andExpect(jsonPath("$.products[0].productId").value(id(lager)))
                .andExpect(jsonPath("$.products[0].quantity").value(5))
                .andExpect(jsonPath("$.products[0].revenueCents").value(3 * 250 + 2 * 300))
                .andExpect(jsonPath("$.products[0].months[0].month").value("2099-10"))
                .andExpect(jsonPath("$.products[0].months[0].quantity").value(3))
                .andExpect(jsonPath("$.products[0].months[1].month").value("2099-11"))
                .andExpect(jsonPath("$.products[0].months[1].quantity").value(2))
                .andExpect(jsonPath("$.products[1].productId").value(id(stout)))
                .andExpect(jsonPath("$.products[1].months.length()").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(9))
                .andExpect(jsonPath("$.totalRevenueCents").value(1350 + 1800));
    }

    @Test
    void undonePurchases_doNotCount() throws Exception {
        sale(at(2099, 10, 5, 20, 0), lager, 3, 250, 110L);
        Purchase undone = sale(at(2099, 10, 6, 20, 0), lager, 50, 250, 110L);
        undone.setStatus(PurchaseStatusEnum.REVERSED);
        forget();

        report("2099-10-01", "2099-10-31")
                .andExpect(jsonPath("$.products[0].quantity").value(3))
                .andExpect(jsonPath("$.totalQuantity").value(3));
    }

    @Test
    void activeProductsThatSoldNothing_comeLastWithZero_hiddenOnesNot() throws Exception {
        Product hiddenUnsold = product("Aruande Peidetud", 300, null, false);
        Product hiddenSold = product("Aruande Peidetud müüdud", 300, null, false);
        sale(at(2099, 10, 5, 20, 0), hiddenSold, 1, 300, null);

        String json = reportJson("2099-10-01", "2099-10-31");

        List<Integer> ids = JsonPath.read(json, "$.products[*].productId");
        assertThat(ids.getFirst()).isEqualTo(id(hiddenSold));
        assertThat(ids).contains(id(lager), id(stout)).doesNotContain(id(hiddenUnsold));
        assertThat(JsonPath.<List<Integer>>read(json, "$.products[?(@.productId == " + lager.getId() + ")].quantity"))
                .containsExactly(0);
        assertThat(JsonPath.<List<Integer>>read(json, "$.products[1:].quantity")).containsOnly(0);
    }

    // ---- days in club time ----

    @Test
    void bothDaysAreIncluded_inClubTime() throws Exception {
        sale(at(2099, 10, 4, 23, 59).plusSeconds(59), lager, 1, 250, 110L);
        sale(at(2099, 10, 5, 0, 0), lager, 10, 250, 110L);
        sale(at(2099, 10, 6, 23, 59).plusSeconds(59), lager, 100, 250, 110L);
        sale(at(2099, 10, 7, 0, 0), lager, 1000, 250, 110L);

        report("2099-10-05", "2099-10-06")
                .andExpect(jsonPath("$.products[0].quantity").value(110));
    }

    @Test
    void purchaseJustAfterMidnight_countsForTheNewDayAndMonth() throws Exception {
        sale(at(2099, 11, 1, 0, 30), lager, 7, 250, 110L);

        report("2099-11-01", "2099-11-01")
                .andExpect(jsonPath("$.products[0].quantity").value(7))
                .andExpect(jsonPath("$.products[0].months[0].month").value("2099-11"));
        report("2099-10-31", "2099-10-31")
                .andExpect(jsonPath("$.totalQuantity").value(0));
    }

    // ---- cost and margin ----

    @Test
    void margin_usesThePurchasePriceStoredWithEachSale() throws Exception {
        sale(at(2099, 10, 5, 20, 0), lager, 2, 250, 100L);
        sale(at(2099, 10, 6, 20, 0), lager, 3, 250, 120L);
        entityManager.find(Product.class, lager.getId()).setCostCents(999L);
        forget();

        report("2099-10-01", "2099-10-31")
                .andExpect(jsonPath("$.products[0].costCents").value(2 * 100 + 3 * 120))
                .andExpect(jsonPath("$.products[0].marginCents").value(5 * 250 - 560))
                .andExpect(jsonPath("$.products[0].unitsWithoutCost").value(0));
    }

    @Test
    void olderSaleWithoutStoredCost_usesTheProductsCurrentPurchasePrice() throws Exception {
        sale(at(2099, 10, 5, 20, 0), lager, 4, 250, null);

        report("2099-10-01", "2099-10-31")
                .andExpect(jsonPath("$.products[0].costCents").value(4 * 110))
                .andExpect(jsonPath("$.products[0].marginCents").value(4 * (250 - 110)));
    }

    @Test
    void productWithoutAnyPurchasePrice_hasNoMargin_andIsLeftOutOfTheMarginTotals() throws Exception {
        Product unpriced = product("Aruande Ostuhinnata", 300, null, true);
        sale(at(2099, 10, 5, 20, 0), unpriced, 6, 300, null);
        sale(at(2099, 10, 5, 20, 0), lager, 2, 250, 110L);

        report("2099-10-01", "2099-10-31")
                .andExpect(jsonPath("$.products[0].productId").value(id(unpriced)))
                .andExpect(jsonPath("$.products[0].costCents").doesNotExist())
                .andExpect(jsonPath("$.products[0].marginCents").doesNotExist())
                .andExpect(jsonPath("$.products[0].unitsWithoutCost").value(6))
                .andExpect(jsonPath("$.totalRevenueCents").value(1800 + 500))
                .andExpect(jsonPath("$.totalCostCents").value(220))
                .andExpect(jsonPath("$.totalMarginCents").value(500 - 220))
                .andExpect(jsonPath("$.unitsWithoutCost").value(6));
    }

    // ---- range ----

    @Test
    void withoutTo_endsToday() throws Exception {
        String json = mockMvc.perform(get("/api/admin/reports/sales").param("from", "2026-01-01")
                        .with(csrf()).session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(json, "$.to")).isEqualTo(LocalDate.now(clock).toString());
    }

    @Test
    void withoutFrom_startsAtTheFirstSale() throws Exception {
        OffsetDateTime firstSale = entityManager.createQuery(
                        "select min(p.createdAt) from Purchase p where p.status = :status", OffsetDateTime.class)
                .setParameter("status", PurchaseStatusEnum.COMPLETED).getSingleResult();

        String json = mockMvc.perform(get("/api/admin/reports/sales").with(csrf()).session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(json, "$.from"))
                .isEqualTo(firstSale.atZoneSameInstant(ClockConfig.APP_ZONE).toLocalDate().toString());
    }

    @Test
    void fromAfterTo_returns400() throws Exception {
        report("2099-10-02", "2099-10-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(142));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2099-13-01", "01.10.2099", "2099-10"})
    void malformedDate_returns400(String date) throws Exception {
        report(date, "2099-10-31")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    @Test
    void byMember_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/reports/sales").with(csrf()).session(testSessions.memberSession()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- helpers ----

    private ResultActions report(String from, String to) throws Exception {
        forget();
        return mockMvc.perform(get("/api/admin/reports/sales").param("from", from).param("to", to)
                .with(csrf()).session(adminSession));
    }

    private String reportJson(String from, String to) throws Exception {
        return report(from, to).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static OffsetDateTime at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ClockConfig.APP_ZONE).toOffsetDateTime();
    }

    private Product product(String name, long priceCents, Long costCents, boolean active) {
        Product product = testData.product(name + " " + UUID.randomUUID(), priceCents, 950, active);
        product.setCostCents(costCents);
        entityManager.flush();
        return product;
    }

    // Purchase and line only: the report reads purchase lines
    private Purchase sale(OffsetDateTime createdAt, Product product, int quantity, long unitPriceCents, Long unitCostCents) {
        Purchase purchase = new Purchase();
        purchase.setMember(entityManager.find(Member.class, buyer.getId()));
        purchase.setTotalCents(quantity * unitPriceCents);
        purchase.setStatus(PurchaseStatusEnum.COMPLETED);
        purchase.setCreatedAt(createdAt);
        entityManager.persist(purchase);
        PurchaseLine line = new PurchaseLine();
        line.setPurchase(purchase);
        line.setProduct(entityManager.find(Product.class, product.getId()));
        line.setQuantity(quantity);
        line.setUnitPriceCents(unitPriceCents);
        line.setUnitCostCents(unitCostCents);
        entityManager.persist(line);
        entityManager.flush();
        return purchase;
    }

    private static int id(Product product) {
        return Math.toIntExact(product.getId());
    }

    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
