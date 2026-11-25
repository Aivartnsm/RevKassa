package ee.revkassa.controller.me;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.service.AccountEntryService;
import ee.revkassa.service.AccountPosting;
import ee.revkassa.service.AccountService;
import ee.revkassa.service.StockMove;
import ee.revkassa.service.StockService;
import ee.revkassa.testdata.TestClock;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B14: GET /api/me/entries through the real security chain and PostgreSQL, with a clock the test moves so that
 * every row has its own time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AccountEntryFlowTest {

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
    private AccountService accountService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TestClock clock;

    private Member member;
    private MockHttpSession memberSession;
    private Member admin;
    private Product lager;
    private Product stout;

    @BeforeEach
    void createMemberAndProducts() throws Exception {
        member = testSessions.createMember(MemberRoleEnum.MEMBER);
        memberSession = testSessions.passwordSession(member);
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        lager = testData.product("Saku Test Originaal", 250, 1100, true);
        stout = testData.product("Test Õllenaut IPA", 400, 1101, true);
        stockService.move(StockMove.delivery(lager, 10, null, null, admin));
        stockService.move(StockMove.delivery(stout, 10, null, null, admin));
    }

    // ---- descriptions ----

    @Test
    void noEntries_returnsEmptyFirstPage() throws Exception {
        entries(memberSession, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void purchase_isDescribedByItsLinesWithAmountAndPurchaseId() throws Exception {
        long purchaseId = buy(900, line(lager, 2), line(stout, 1));

        entries(memberSession, 0)
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$.entries[0].amountCents").value(-900))
                .andExpect(jsonPath("$.entries[0].description").value("2 × Saku Test Originaal, 1 × Test Õllenaut IPA"))
                .andExpect(jsonPath("$.entries[0].purchaseId").value(purchaseId))
                .andExpect(jsonPath("$.entries[0].createdAt").value(startsWith("2026-11-25T20:")))
                .andExpect(jsonPath("$.entries[0].entryId").isNumber());
    }

    @Test
    void undonePurchase_showsReversalAboveThePurchase() throws Exception {
        long purchaseId = buy(250, line(lager, 1));
        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(post("/api/purchases/" + purchaseId + "/undo").with(csrf()).session(memberSession))
                .andExpect(status().isOk());
        forget();

        entries(memberSession, 0)
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].type").value("PURCHASE_REVERSAL"))
                .andExpect(jsonPath("$.entries[0].amountCents").value(250))
                .andExpect(jsonPath("$.entries[0].description").value("Tagasivõetud ost: 1 × Saku Test Originaal"))
                .andExpect(jsonPath("$.entries[0].purchaseId").value(purchaseId))
                .andExpect(jsonPath("$.entries[1].type").value("PURCHASE"));
    }

    @Test
    void adjustment_showsTheAdminsNoteWithoutPurchase() throws Exception {
        accountService.post(AccountPosting.adjustment(member, 500, "Sularaha baaris", admin));
        forget();

        entries(memberSession, 0)
                .andExpect(jsonPath("$.entries[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.entries[0].amountCents").value(500))
                .andExpect(jsonPath("$.entries[0].description").value("Parandus · Sularaha baaris"))
                .andExpect(jsonPath("$.entries[0].purchaseId").value(nullValue()));
    }

    // ---- pages ----

    @Test
    void pages_comeNewestFirstWithHasMoreUntilTheLastPage() throws Exception {
        int rows = AccountEntryService.PAGE_SIZE + 3;
        for (int n = 1; n <= rows; n++) {
            accountService.post(AccountPosting.adjustment(member, n, "rida " + n, admin));
            clock.advance(Duration.ofSeconds(1));
        }
        forget();

        entries(memberSession, 0)
                .andExpect(jsonPath("$.entries.length()").value(AccountEntryService.PAGE_SIZE))
                .andExpect(jsonPath("$.entries[0].description").value("Parandus · rida " + rows))
                .andExpect(jsonPath("$.entries[19].description").value("Parandus · rida 4"))
                .andExpect(jsonPath("$.hasMore").value(true));
        entries(memberSession, 1)
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].description").value("Parandus · rida 3"))
                .andExpect(jsonPath("$.entries[2].description").value("Parandus · rida 1"))
                .andExpect(jsonPath("$.hasMore").value(false));
        entries(memberSession, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void exactlyOneFullPage_hasNoMore() throws Exception {
        for (int n = 1; n <= AccountEntryService.PAGE_SIZE; n++) {
            accountService.post(AccountPosting.adjustment(member, n, "rida " + n, admin));
        }
        forget();

        entries(memberSession, 0)
                .andExpect(jsonPath("$.entries.length()").value(AccountEntryService.PAGE_SIZE))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void sameMoment_isOrderedByNewestRowFirst() throws Exception {
        accountService.post(AccountPosting.adjustment(member, 1, "esimene", admin));
        accountService.post(AccountPosting.adjustment(member, 2, "teine", admin));
        forget();

        entries(memberSession, 0)
                .andExpect(jsonPath("$.entries[0].description").value("Parandus · teine"))
                .andExpect(jsonPath("$.entries[1].description").value("Parandus · esimene"));
    }

    @Test
    void withoutPage_returnsFirstPage() throws Exception {
        buy(250, line(lager, 1));

        mockMvc.perform(get("/api/me/entries").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "10001"})
    void pageOutOfRange_returns400(String page) throws Exception {
        mockMvc.perform(get("/api/me/entries").param("page", page).session(memberSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));
    }

    @Test
    void pageNotANumber_returns400() throws Exception {
        mockMvc.perform(get("/api/me/entries").param("page", "abc").session(memberSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    // ---- only your own ----

    @Test
    void anotherMembersRows_areNotShown() throws Exception {
        buy(250, line(lager, 1));
        Member other = testSessions.createMember(MemberRoleEnum.MEMBER);
        accountService.post(AccountPosting.adjustment(other, 100, "teise liikme rida", admin));
        forget();

        entries(memberSession, 0)
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].type").value("PURCHASE"));
        entries(testSessions.passwordSession(other), 0)
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].description").value("Parandus · teise liikme rida"));
    }

    @Test
    void pinSession_seesOwnHistory() throws Exception {
        entries(testSessions.pinSession(MemberRoleEnum.MEMBER), 0).andExpect(status().isOk());
    }

    @Test
    void withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/me/entries")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private ResultActions entries(MockHttpSession session, int page) throws Exception {
        return mockMvc.perform(get("/api/me/entries").param("page", String.valueOf(page)).session(session));
    }

    private long buy(long expectedTotalCents, String... lines) throws Exception {
        String body = mockMvc.perform(post("/api/purchases").with(csrf()).session(memberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"expectedTotalCents\":" + expectedTotalCents
                                + ",\"lines\":[" + String.join(",", lines) + "]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        forget();
        return Long.parseLong(body.replaceAll(".*\"purchaseId\":(\\d+).*", "$1"));
    }

    private static String line(Product product, int quantity) {
        return "{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}";
    }

    // All requests share the test transaction: write and forget, as a commit would between real requests
    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
