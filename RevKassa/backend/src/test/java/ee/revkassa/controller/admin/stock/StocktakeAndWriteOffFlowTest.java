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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B09: stocktake (counted quantity becomes the stock) and write-offs, through the real security chain and PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class StocktakeAndWriteOffFlowTest {

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
    private Member admin;
    private Product lager;
    private Product stout;
    private Product cider;

    @BeforeEach
    void createProductsWithStock() throws Exception {
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        adminSession = testSessions.passwordSession(admin);
        lager = productWithStock("Count Test Lager", 24);
        stout = productWithStock("Count Test Stout", 6);
        cider = productWithStock("Count Test Cider", 10);
    }

    // ---- stocktake ----

    @Test
    void stocktake_writesDifferencePerProductAndCountBecomesStock() throws Exception {
        stocktake(adminSession, """
                {"lines":[{"productId":%d,"countedQty":20},{"productId":%d,"countedQty":9}],"note":"Kuu lõpp"}"""
                .formatted(lager.getId(), stout.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].productId").value(lager.getId()))
                .andExpect(jsonPath("$[0].name").value("Count Test Lager"))
                .andExpect(jsonPath("$[0].previousStockQty").value(24))
                .andExpect(jsonPath("$[0].countedQty").value(20))
                .andExpect(jsonPath("$[0].difference").value(-4))
                .andExpect(jsonPath("$[1].difference").value(3));

        assertThat(stockOf(lager)).isEqualTo(20);
        assertThat(stockOf(stout)).isEqualTo(9);
        StockMovement correction = lastMovementOf(lager);
        assertThat(correction.getType()).isEqualTo(StockMovementTypeEnum.STOCKTAKE);
        assertThat(correction.getQuantityDelta()).isEqualTo(-4);
        assertThat(correction.getNote()).isEqualTo("Kuu lõpp");
        assertThat(correction.getCreatedBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    void stocktake_countEqualsStock_writesNothing() throws Exception {
        long movementsBefore = stockMovementRepository.count();

        stocktake(adminSession, """
                {"lines":[{"productId":%d,"countedQty":24},{"productId":%d,"countedQty":10}]}"""
                .formatted(lager.getId(), cider.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].difference").value(0))
                .andExpect(jsonPath("$[1].difference").value(0));

        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
        assertThat(stockOf(lager)).isEqualTo(24);
    }

    @Test
    void stocktake_onlyCountedProductsChange() throws Exception {
        stocktake(adminSession, oneCount(lager, 0)).andExpect(status().isCreated());

        assertThat(stockOf(lager)).isZero();
        assertThat(stockOf(stout)).isEqualTo(6);
        assertThat(stockOf(cider)).isEqualTo(10);
    }

    @Test
    void stocktake_negativeStock_isCorrectedUp() throws Exception {
        stockService.move(StockMove.writeOff(stout, 9, "katki", admin));

        stocktake(adminSession, oneCount(stout, 0))
                .andExpect(jsonPath("$[0].previousStockQty").value(-3))
                .andExpect(jsonPath("$[0].difference").value(3));

        assertThat(stockOf(stout)).isZero();
    }

    @Test
    void stocktake_usesStockAtSaveTime_soSalesAfterPageLoadAreCounted() throws Exception {
        // the admin's page showed 24; two left the fridge before the count was saved
        stockService.move(StockMove.writeOff(lager, 2, "katki", admin));

        stocktake(adminSession, oneCount(lager, 22))
                .andExpect(jsonPath("$[0].previousStockQty").value(22))
                .andExpect(jsonPath("$[0].difference").value(0));
    }

    @Test
    void stocktake_keepsStockEqualToLedgerSum() throws Exception {
        stocktake(adminSession, """
                {"lines":[{"productId":%d,"countedQty":17},{"productId":%d,"countedQty":0}]}"""
                .formatted(lager.getId(), stout.getId()));

        assertThat(stockOf(lager)).isEqualTo(stockMovementRepository.sumQuantityDelta(lager.getId()));
        assertThat(stockOf(stout)).isEqualTo(stockMovementRepository.sumQuantityDelta(stout.getId()));
    }

    @Test
    void stocktake_sameProductTwice_returns403AndWritesNothing() throws Exception {
        long movementsBefore = stockMovementRepository.count();

        stocktake(adminSession, """
                {"lines":[{"productId":%d,"countedQty":20},{"productId":%d,"countedQty":21}]}"""
                .formatted(lager.getId(), lager.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(125));

        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
    }

    @Test
    void stocktake_unknownProduct_returns404AndWritesNothing() throws Exception {
        long movementsBefore = stockMovementRepository.count();

        stocktake(adminSession, """
                {"lines":[{"productId":%d,"countedQty":20},{"productId":%d,"countedQty":1}]}"""
                .formatted(lager.getId(), Long.MAX_VALUE))
                .andExpect(status().isNotFound());

        assertThat(stockMovementRepository.count()).isEqualTo(movementsBefore);
        assertThat(stockOf(lager)).isEqualTo(24);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"lines\":[]}", "{\"lines\":[{\"productId\":PID,\"countedQty\":-1}]}",
            "{\"lines\":[{\"productId\":PID}]}", "{\"lines\":[{\"countedQty\":3}]}"})
    void stocktake_invalidInput_returns400(String body) throws Exception {
        stocktake(adminSession, body.replace("PID", lager.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));
    }

    // ---- write-off ----

    @Test
    void writeOff_lowersStockWithNotedMovement() throws Exception {
        writeOff(adminSession, """
                {"lines":[{"productId":%d,"quantity":2}],"note":"  katki  "}""".formatted(lager.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].productId").value(lager.getId()))
                .andExpect(jsonPath("$[0].stockQty").value(22));

        StockMovement movement = lastMovementOf(lager);
        assertThat(movement.getType()).isEqualTo(StockMovementTypeEnum.WRITE_OFF);
        assertThat(movement.getQuantityDelta()).isEqualTo(-2);
        assertThat(movement.getNote()).isEqualTo("katki");
        assertThat(stockOf(lager)).isEqualTo(22).isEqualTo(stockMovementRepository.sumQuantityDelta(lager.getId()));
    }

    @Test
    void writeOff_canTakeStockBelowZero() throws Exception {
        writeOff(adminSession, """
                {"lines":[{"productId":%d,"quantity":8}],"note":"aegunud"}""".formatted(stout.getId()))
                .andExpect(jsonPath("$[0].stockQty").value(-2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"lines\":[{\"productId\":PID,\"quantity\":2}]}",
            "{\"lines\":[{\"productId\":PID,\"quantity\":2}],\"note\":\"  \"}",
            "{\"lines\":[{\"productId\":PID,\"quantity\":0}],\"note\":\"katki\"}",
            "{\"lines\":[{\"productId\":PID,\"quantity\":-2}],\"note\":\"katki\"}",
            "{\"lines\":[],\"note\":\"katki\"}"})
    void writeOff_invalidInput_returns400AndWritesNothing(String body) throws Exception {
        writeOff(adminSession, body.replace("PID", lager.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));

        assertThat(stockOf(lager)).isEqualTo(24);
    }

    // ---- access ----

    @Test
    void byMemberOrAdminPinSession_returns403() throws Exception {
        for (MockHttpSession session : List.of(testSessions.memberSession(), testSessions.pinSession(MemberRoleEnum.ADMIN))) {
            stocktake(session, oneCount(lager, 0)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(118));
            writeOff(session, "{\"lines\":[{\"productId\":" + lager.getId() + ",\"quantity\":1}],\"note\":\"x\"}")
                    .andExpect(status().isForbidden());
        }
        assertThat(stockOf(lager)).isEqualTo(24);
    }

    @Test
    void withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/stock/stocktake").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(oneCount(lager, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- helpers ----

    private Product productWithStock(String name, int stockQty) {
        Product product = testData.product(name, 250, 950, true);
        stockService.move(StockMove.delivery(product, stockQty, null, null, admin));
        return product;
    }

    private ResultActions stocktake(MockHttpSession session, String body) throws Exception {
        return send("/api/admin/stock/stocktake", session, body);
    }

    private ResultActions writeOff(MockHttpSession session, String body) throws Exception {
        return send("/api/admin/stock/write-offs", session, body);
    }

    private ResultActions send(String path, MockHttpSession session, String body) throws Exception {
        ResultActions result = mockMvc.perform(post(path).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private static String oneCount(Product product, int countedQty) {
        return "{\"lines\":[{\"productId\":" + product.getId() + ",\"countedQty\":" + countedQty + "}]}";
    }

    private int stockOf(Product product) {
        entityManager.flush();
        entityManager.clear();
        return productRepository.findById(product.getId()).orElseThrow().getStockQty();
    }

    private StockMovement lastMovementOf(Product product) {
        return entityManager.createQuery(
                        "select m from StockMovement m join fetch m.createdBy where m.product.id = :id order by m.id desc",
                        StockMovement.class)
                .setParameter("id", product.getId()).setMaxResults(1).getSingleResult();
    }
}
