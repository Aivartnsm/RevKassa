package ee.revkassa.controller.me;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.memberfavourite.MemberFavourite;
import ee.revkassa.persistence.product.Product;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B13: GET and PUT /api/me/favourites through the real security chain and PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class FavouriteFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData testData;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private EntityManager entityManager;

    private Member member;
    private MockHttpSession memberSession;
    private Product lager;
    private Product stout;
    private Product cider;

    @BeforeEach
    void createMemberAndProducts() throws Exception {
        member = testSessions.createMember(MemberRoleEnum.MEMBER);
        memberSession = testSessions.passwordSession(member);
        lager = testData.product("Fav Test Lager", 250, 990, true);
        stout = testData.product("Fav Test Stout", 400, 991, true);
        cider = testData.product("Fav Test Cider", 350, 992, true);
    }

    // ---- reading and replacing ----

    @Test
    void noFavouritesYet_returnsEmptyList() throws Exception {
        getFavourites(memberSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productIds.length()").value(0));
    }

    @Test
    void replace_savesListInGivenOrderWithPositionsFromZero() throws Exception {
        replace(memberSession, stout, lager, cider)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productIds").value(ids(stout, lager, cider)));

        getFavourites(memberSession).andExpect(jsonPath("$.productIds").value(ids(stout, lager, cider)));
        assertThat(rowsOf(member)).extracting(MemberFavourite::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    void replaceAgain_reordersAndDropsTheMissingOnes() throws Exception {
        replace(memberSession, lager, stout, cider);

        replace(memberSession, cider, lager).andExpect(status().isOk());

        getFavourites(memberSession).andExpect(jsonPath("$.productIds").value(ids(cider, lager)));
        assertThat(rowsOf(member)).extracting(MemberFavourite::getPosition).containsExactly(0, 1);
    }

    @Test
    void emptyList_clearsFavourites() throws Exception {
        replace(memberSession, lager, stout);

        replace(memberSession).andExpect(status().isOk()).andExpect(jsonPath("$.productIds.length()").value(0));

        assertThat(rowsOf(member)).isEmpty();
    }

    @Test
    void hiddenProduct_staysInListAndCanBeSavedAgain() throws Exception {
        Product hidden = testData.product("Fav Test Hidden", 300, 993, false);

        replace(memberSession, hidden, lager).andExpect(status().isOk());

        getFavourites(memberSession).andExpect(jsonPath("$.productIds").value(ids(hidden, lager)));
    }

    @Test
    void hundredFavourites_areAccepted() throws Exception {
        List<Long> productIds = LongStream.range(0, 100)
                .mapToObj(n -> testData.product("Fav Test Many " + n, 100, 1000 + (int) n, true).getId()).toList();

        send(memberSession, "{\"productIds\":" + productIds + "}").andExpect(status().isOk());

        assertThat(rowsOf(member)).hasSize(100);
    }

    // ---- only your own ----

    @Test
    void eachMemberSeesOnlyTheirOwnList() throws Exception {
        Member other = testSessions.createMember(MemberRoleEnum.MEMBER);
        MockHttpSession otherSession = testSessions.passwordSession(other);
        replace(memberSession, lager, stout);

        replace(otherSession, cider).andExpect(status().isOk());

        getFavourites(memberSession).andExpect(jsonPath("$.productIds").value(ids(lager, stout)));
        getFavourites(otherSession).andExpect(jsonPath("$.productIds").value(ids(cider)));
    }

    @Test
    void pinSession_canReplaceOwnFavourites() throws Exception {
        MockHttpSession pinSession = testSessions.pinSession(MemberRoleEnum.MEMBER);

        replace(pinSession, stout).andExpect(status().isOk());

        getFavourites(pinSession).andExpect(jsonPath("$.productIds").value(ids(stout)));
    }

    // ---- refused ----

    @Test
    void unknownProduct_returns400AndKeepsOldList() throws Exception {
        replace(memberSession, lager);

        send(memberSession, "{\"productIds\":[" + stout.getId() + "," + Long.MAX_VALUE + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(130));

        getFavourites(memberSession).andExpect(jsonPath("$.productIds").value(ids(lager)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"productIds\":null}", "{\"productIds\":[LAGER,LAGER]}", "{\"productIds\":[null]}",
            "{\"productIds\":[0]}", "{\"productIds\":[-1]}", "{\"productIds\":[\"x\"]}"})
    void invalidList_returns400AndKeepsOldList(String body) throws Exception {
        replace(memberSession, stout);

        send(memberSession, body.replace("LAGER", lager.getId().toString())).andExpect(status().isBadRequest());

        getFavourites(memberSession).andExpect(jsonPath("$.productIds").value(ids(stout)));
    }

    @Test
    void moreThan100_returns400() throws Exception {
        String tooMany = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));

        send(memberSession, "{\"productIds\":[" + tooMany + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));
    }

    @Test
    void withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/me/favourites")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me/favourites").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(put("/api/me/favourites").session(memberSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[" + lager.getId() + "]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));

        assertThat(rowsOf(member)).isEmpty();
    }

    // ---- helpers ----

    private ResultActions getFavourites(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/api/me/favourites").session(session));
    }

    private ResultActions replace(MockHttpSession session, Product... products) throws Exception {
        return send(session, "{\"productIds\":" + ids(products) + "}");
    }

    private ResultActions send(MockHttpSession session, String body) throws Exception {
        ResultActions result = mockMvc.perform(put("/api/me/favourites").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    // As jsonPath reads JSON numbers (ids of a test database fit an int), in a list type it can convert to
    private static List<Integer> ids(Product... products) {
        return Arrays.stream(products).map(product -> Math.toIntExact(product.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<MemberFavourite> rowsOf(Member someone) {
        return entityManager.createQuery(
                        "select f from MemberFavourite f where f.member.id = :id order by f.position", MemberFavourite.class)
                .setParameter("id", someone.getId()).getResultList();
    }
}
