package ee.revkassa.controller.admin.stock;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.product.ProductRepository;
import ee.revkassa.persistence.stockmovement.StockMovement;
import ee.revkassa.persistence.stockmovement.StockMovementRepository;
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

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B08: the stock ledger and deliveries. The key invariant: product.stock_qty = sum(stock_movement.quantity_delta).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class StockFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData testData;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private StockService stockService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;
    @Autowired
    private EntityManager entityManager;

    private MockHttpSession adminSession;
    private Product lager;
    private Product stout;

    @BeforeEach
    void createProducts() throws Exception {
        adminSession = testSessions.adminSession();
        lager = testData.product("Stock Test Lager", 250, 900, true);
        stout = testData.product("Stock Test Stout", 400, 901, true);
    }

    // ---- delivery ----

    @Test
    void delivery_raisesStockAndReturnsNewStockPerProduct() throws Exception {
        deliver(adminSession, """
                {"lines":[{"productId":%d,"quantity":24,"unitCostCents":120},{"productId":%d,"quantity":6}],
                 "note":"  Selver  "}""".formatted(lager.getId(), stout.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].productId").value(lager.getId()))
                .andExpect(jsonPath("$[0].name").value("Stock Test Lager"))
                .andExpect(jsonPath("$[0].stockQty").value(24))
                .andExpect(jsonPath("$[1].productId").value(stout.getId()))
                .andExpect(jsonPath("$[1].stockQty").value(6));

        assertThat(stockOf(lager)).isEqualTo(24);
        assertThat(stockOf(stout)).isEqualTo(6);
    }

    @Test
    void delivery_writesOneDeliveryMovementPerLine() throws Exception {
        Member admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        MockHttpSession session = testSessions.passwordSession(admin);

        deliver(session, """
                {"lines":[{"productId":%d,"quantity":24,"unitCostCents":120},{"productId":%d,"quantity":6}],
                 "note":"  Selver  "}""".formatted(lager.getId(), stout.getId()));

        List<StockMovement> movements = movementsOf(lager);
        assertThat(movements).hasSize(1);
        StockMovement movement = movements.getFirst();
        assertThat(movement.getType()).isEqualTo(StockMovementTypeEnum.DELIVERY);
        assertThat(movement.getQuantityDelta()).isEqualTo(24);
        assertThat(movement.getUnitCostCents()).isEqualTo(120);
        assertThat(movement.getNote()).isEqualTo("Selver");
        assertThat(movement.getCreatedBy().getId()).isEqualTo(admin.getId());
        assertThat(movementsOf(stout).getFirst().getUnitCostCents()).isNull();
    }

    @Test
    void deliveries_keepStockEqualToLedgerSum() throws Exception {
        deliver(adminSession, oneLine(lager, 24));
        deliver(adminSession, """
                {"lines":[{"productId":%d,"quantity":12},{"productId":%d,"quantity":6},{"productId":%d,"quantity":1}]}"""
                .formatted(lager.getId(), stout.getId(), lager.getId()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stockQty").value(37));

        assertThat(stockOf(lager)).isEqualTo(37).isEqualTo(stockMovementRepository.sumQuantityDelta(lager.getId()));
        assertThat(stockOf(stout)).isEqualTo(6).isEqualTo(stockMovementRepository.sumQuantityDelta(stout.getId()));
    }

    @Test
    void invariant_holdsForEveryProductAfterDeliveries() throws Exception {
        deliver(adminSession, oneLine(lager, 24));
        deliver(adminSession, oneLine(stout, 3));

        List<?> mismatches = entityManager.createNativeQuery("""
                select p.id from product p left join stock_movement m on m.product_id = p.id
                group by p.id, p.stock_qty having p.stock_qty <> coalesce(sum(m.quantity_delta), 0)""").getResultList();
        assertThat(mismatches).isEmpty();
    }

    @Test
    void delivery_hiddenProduct_canBeRestocked() throws Exception {
        Product hidden = testData.product("Stock Test Hidden", 250, 902, false);

        deliver(adminSession, oneLine(hidden, 10)).andExpect(status().isCreated());

        assertThat(stockOf(hidden)).isEqualTo(10);
    }

    @Test
    void delivery_unknownProduct_returns404AndSavesNothing() throws Exception {
        long movementsBefore = stockMovementRepository.count();

        deliver(adminSession, """
                {"lines":[{"productId":%d,"quantity":24},{"productId":%d,"quantity":6}]}"""
                .formatted(lager.getId(), Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));

        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
        assertThat(stockOf(lager)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"lines\":[]}",
            "{}",
            "{\"lines\":[{\"productId\":PID,\"quantity\":0}]}",
            "{\"lines\":[{\"productId\":PID,\"quantity\":-5}]}",
            "{\"lines\":[{\"productId\":PID}]}",
            "{\"lines\":[{\"quantity\":5}]}",
            "{\"lines\":[{\"productId\":PID,\"quantity\":5,\"unitCostCents\":-1}]}",
            "{\"lines\":[null]}"})
    void delivery_invalidInput_returns400AndSavesNothing(String body) throws Exception {
        deliver(adminSession, body.replace("PID", lager.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));

        assertThat(movementsOf(lager)).isEmpty();
    }

    @Test
    void delivery_moreThan100Lines_returns400() throws Exception {
        String line = "{\"productId\":" + lager.getId() + ",\"quantity\":1}";
        String body = "{\"lines\":[" + String.join(",", Collections.nCopies(101, line)) + "]}";

        deliver(adminSession, body).andExpect(status().isBadRequest());
    }

    @Test
    void delivery_byMemberOrAdminPinSession_returns403() throws Exception {
        for (MockHttpSession session : List.of(testSessions.memberSession(), testSessions.pinSession(MemberRoleEnum.ADMIN))) {
            deliver(session, oneLine(lager, 24))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(118));
        }
        assertThat(stockOf(lager)).isZero();
    }

    @Test
    void delivery_withoutSession_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/stock/deliveries").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(oneLine(lager, 24)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delivery_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/stock/deliveries").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(oneLine(lager, 24)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- stock and the product form ----

    @Test
    void delivery_doesNotChangeProductVersion_soOpenProductFormStillSavesAndKeepsStock() throws Exception {
        long versionInForm = lager.getVersion();
        deliver(adminSession, oneLine(lager, 24));

        mockMvc.perform(put("/api/admin/products/" + lager.getId()).with(csrf()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Stock Test Lager","priceCents":270,"lowStockThreshold":6,
                                 "sortOrder":900,"active":true,"version":%d}""".formatted(versionInForm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQty").value(24));

        assertThat(stockOf(lager)).isEqualTo(24);
    }

    // ---- StockService.move ----

    @Test
    void move_canTakeStockBelowZero() {
        Member admin = testSessions.createMember(MemberRoleEnum.ADMIN);

        int stock = stockService.move(StockMove.writeOff(lager, 5, "katki", admin));

        assertThat(stock).isEqualTo(-5);
        assertThat(stockMovementRepository.sumQuantityDelta(lager.getId())).isEqualTo(-5);
    }

    @Test
    void move_zeroOrWrongSignQuantity_isRefused() {
        Member admin = testSessions.createMember(MemberRoleEnum.ADMIN);

        assertThatThrownBy(() -> stockService.move(StockMove.delivery(lager, 0, null, null, admin)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stockService.move(StockMove.delivery(lager, -3, null, null, admin)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(movementsOf(lager)).isEmpty();
    }

    // ---- helpers ----

    private ResultActions deliver(MockHttpSession session, String body) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/admin/stock/deliveries").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private static String oneLine(Product product, int quantity) {
        return "{\"lines\":[{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}]}";
    }

    private int stockOf(Product product) {
        entityManager.clear();
        return productRepository.findById(product.getId()).orElseThrow().getStockQty();
    }

    private List<StockMovement> movementsOf(Product product) {
        return entityManager.createQuery(
                        "select m from StockMovement m join fetch m.createdBy where m.product.id = :id order by m.id",
                        StockMovement.class)
                .setParameter("id", product.getId()).getResultList();
    }
}
