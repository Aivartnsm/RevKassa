package ee.revkassa.controller.product;

import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.product.ProductRepository;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B07: products for members and product management for admins, through the real security chain and PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class ProductFlowTest {

    private static final String VALID_PRODUCT = """
            {"name":"  Põhjala Öö  ","style":"Stout","volumeMl":330,"priceCents":450,
             "lowStockThreshold":12,"sortOrder":7,"active":true}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData testData;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private EntityManager entityManager;

    private MockHttpSession adminSession;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminSession = testSessions.adminSession();
    }

    // ---- member view ----

    @Test
    void memberList_showsOnlyActiveProductsInSortOrderWithPriceAndStock() throws Exception {
        Product later = testData.product("Test Later", 300, 900, true);
        Product earlier = testData.product("Test Earlier", 250, 899, true);
        Product hidden = testData.product("Test Hidden", 250, 898, false);

        String body = mockMvc.perform(get("/api/products").session(testSessions.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(hidden.getId().intValue()))))
                .andExpect(jsonPath("$[?(@.id == " + earlier.getId() + ")].priceCents").value(250))
                .andExpect(jsonPath("$[?(@.id == " + earlier.getId() + ")].stockQty").value(0))
                .andExpect(jsonPath("$[*].active").doesNotExist())
                .andExpect(jsonPath("$[*].version").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body.indexOf("\"id\":" + earlier.getId() + ",")).isLessThan(body.indexOf("\"id\":" + later.getId() + ","));
    }

    @Test
    void memberList_worksInPinSession() throws Exception {
        mockMvc.perform(get("/api/products").session(testSessions.pinSession(MemberRoleEnum.MEMBER)))
                .andExpect(status().isOk());
    }

    @Test
    void memberList_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(115));
    }

    // ---- admin list ----

    @Test
    void adminList_includesHiddenProductsWithVersion() throws Exception {
        Product hidden = testData.product("Test Hidden", 250, 898, false);

        mockMvc.perform(get("/api/admin/products").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + hidden.getId() + ")].active").value(false))
                .andExpect(jsonPath("$[?(@.id == " + hidden.getId() + ")].version").value(0))
                .andExpect(jsonPath("$[?(@.id == " + hidden.getId() + ")].lowStockThreshold").value(6));
    }

    @Test
    void adminEndpoints_memberOrPinSession_returns403() throws Exception {
        for (MockHttpSession session : List.of(testSessions.memberSession(), testSessions.pinSession(MemberRoleEnum.ADMIN))) {
            mockMvc.perform(get("/api/admin/products").session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(118));
            create(session, VALID_PRODUCT).andExpect(status().isForbidden());
        }
    }

    // ---- create ----

    @Test
    void create_savesProductWithZeroStockAndTrimmedName() throws Exception {
        String body = create(adminSession, VALID_PRODUCT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Põhjala Öö"))
                .andExpect(jsonPath("$.style").value("Stout"))
                .andExpect(jsonPath("$.volumeMl").value(330))
                .andExpect(jsonPath("$.priceCents").value(450))
                .andExpect(jsonPath("$.stockQty").value(0))
                .andExpect(jsonPath("$.lowStockThreshold").value(12))
                .andExpect(jsonPath("$.sortOrder").value(7))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();

        Product saved = productRepository.findById(idOf(body)).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Põhjala Öö");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void create_stockInBody_isIgnored() throws Exception {
        create(adminSession, VALID_PRODUCT.replace("\"active\":true", "\"active\":true,\"stockQty\":999"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockQty").value(0));
    }

    @Test
    void create_blankStyleAndNoVolume_areStoredAsEmpty() throws Exception {
        create(adminSession, """
                {"name":"Kali","style":"  ","priceCents":150,"lowStockThreshold":0,"sortOrder":0,"active":true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.volumeMl").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"priceCents\":450|\"priceCents\":0", "\"priceCents\":450|\"priceCents\":-250",
            "\"priceCents\":450|\"priceCents\":null", "\"name\":\"  Põhjala Öö  \"|\"name\":\"  \"",
            "\"volumeMl\":330|\"volumeMl\":0", "\"lowStockThreshold\":12|\"lowStockThreshold\":-1",
            "\"active\":true|\"active\":null"})
    void create_invalidField_returns400(String replacement) throws Exception {
        String[] parts = replacement.split("\\|");
        String field = parts[0].substring(1, parts[0].indexOf('"', 1));

        create(adminSession, VALID_PRODUCT.replace(parts[0], parts[1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103))
                .andExpect(jsonPath("$.fieldErrors." + field).exists());
    }

    @Test
    void create_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/products").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- update ----

    @Test
    void update_changesFieldsAndReturnsNewVersion() throws Exception {
        Product product = testData.product("Saku Test", 250, 5, true);
        product.setStockQty(24);
        entityManager.flush();
        long version = product.getVersion();

        update(product.getId(), """
                {"name":"Saku Test 2","style":null,"volumeMl":330,"priceCents":280,
                 "lowStockThreshold":10,"sortOrder":2,"active":false,"version":%d}""".formatted(version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Saku Test 2"))
                .andExpect(jsonPath("$.style").doesNotExist())
                .andExpect(jsonPath("$.priceCents").value(280))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.stockQty").value(24))
                .andExpect(jsonPath("$.version").value(version + 1));

        entityManager.clear();
        Product saved = productRepository.findById(product.getId()).orElseThrow();
        assertThat(saved.getPriceCents()).isEqualTo(280);
        assertThat(saved.getStockQty()).isEqualTo(24);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(saved.getCreatedAt());
    }

    @Test
    void update_staleVersion_returns409AndChangesNothing() throws Exception {
        Product product = testData.product("Saku Test", 250, 5, true);
        update(product.getId(), updateBody(300, 0)).andExpect(status().isOk());

        update(product.getId(), updateBody(999, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(105));

        entityManager.clear();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getPriceCents()).isEqualTo(300);
    }

    @Test
    void update_missingVersion_returns400() throws Exception {
        Product product = testData.product("Saku Test", 250, 5, true);

        update(product.getId(), VALID_PRODUCT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());
    }

    @Test
    void update_zeroPrice_returns400() throws Exception {
        Product product = testData.product("Saku Test", 250, 5, true);

        update(product.getId(), updateBody(0, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priceCents").exists());
    }

    @Test
    void update_unknownProduct_returns404() throws Exception {
        update(Long.MAX_VALUE, updateBody(300, 0))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    void update_byMember_returns403AndChangesNothing() throws Exception {
        Product product = testData.product("Saku Test", 250, 5, true);

        mockMvc.perform(put("/api/admin/products/" + product.getId()).with(csrf()).session(testSessions.memberSession())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody(1, 0)))
                .andExpect(status().isForbidden());

        entityManager.clear();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getPriceCents()).isEqualTo(250);
    }

    // ---- helpers ----

    private ResultActions create(MockHttpSession session, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/products").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(Long productId, String body) throws Exception {
        ResultActions result = mockMvc.perform(put("/api/admin/products/" + productId).with(csrf()).session(adminSession)
                .contentType(MediaType.APPLICATION_JSON).content(body));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private static String updateBody(long priceCents, long version) {
        return """
                {"name":"Saku Test","style":"Lager","volumeMl":500,"priceCents":%d,
                 "lowStockThreshold":6,"sortOrder":5,"active":true,"version":%d}""".formatted(priceCents, version);
    }

    private static Long idOf(String body) {
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
