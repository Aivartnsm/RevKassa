package ee.revkassa.controller.admin.membershipfee;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershipfee.MembershipFee;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B22: monthly fees per level through the real security chain and PostgreSQL. A fee can be set from the current month
 * on, and only for months not yet charged for the level, so charged months never change. Every test uses its own new
 * levels, so the dev data's fees do not matter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminMembershipFeeFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private Member admin;
    private MockHttpSession adminSession;
    private YearMonth thisMonth;
    private MembershipLevel level;
    private MembershipLevel otherLevel;

    @BeforeEach
    void createAdminAndLevels() throws Exception {
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        admin.setDisplayName("Tasude Admin");
        adminSession = testSessions.passwordSession(admin);
        thisMonth = YearMonth.now(clock);
        level = level("Täistase", true);
        otherLevel = level("Teine tase", true);
        forget();
    }

    // ---- list ----

    @Test
    void levelWithoutFees_showsZeroAndEmptyHistory() throws Exception {
        getFees()
                .andExpect(status().isOk())
                .andExpect(jsonPath(levelPath(level) + ".name").value(level.getName()))
                .andExpect(jsonPath(levelPath(level) + ".active").value(true))
                .andExpect(jsonPath(levelPath(level) + ".currentAmountCents").value(0))
                .andExpect(jsonPath(levelPath(level) + ".upcoming").value(contains(nullValue())))
                .andExpect(jsonPath(levelPath(level) + ".history.length()").value(0));
    }

    @Test
    void list_showsCurrentFromHistoryAndOlderRowsNewestFirst() throws Exception {
        pastFee(level, thisMonth.minusMonths(3), 400);
        pastFee(level, thisMonth.minusMonths(1), 500);

        getFees()
                .andExpect(jsonPath(levelPath(level) + ".currentAmountCents").value(500))
                .andExpect(jsonPath(levelPath(level) + ".history[0].validFrom").value(thisMonth.minusMonths(1).toString()))
                .andExpect(jsonPath(levelPath(level) + ".history[0].amountCents").value(500))
                .andExpect(jsonPath(levelPath(level) + ".history[1].validFrom").value(thisMonth.minusMonths(3).toString()))
                .andExpect(jsonPath(levelPath(level) + ".history[1].amountCents").value(400));
    }

    @Test
    void list_byMember_returns403() throws Exception {
        send(get("/api/admin/fees"), testSessions.memberSession())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void list_byAdminWithPin_returns403() throws Exception {
        send(get("/api/admin/fees"), testSessions.pinSession(MemberRoleEnum.ADMIN))
                .andExpect(status().isForbidden());
    }

    // ---- save ----

    @Test
    void save_nextMonth_isUpcomingAndKeepsTheCurrentFee() throws Exception {
        pastFee(level, thisMonth.minusMonths(1), 3500);

        save(thisMonth.plusMonths(1), fee(level, 3740))
                .andExpect(status().isOk())
                .andExpect(jsonPath(levelPath(level) + ".currentAmountCents").value(3500))
                .andExpect(jsonPath(levelPath(level) + ".upcoming.validFrom").value(thisMonth.plusMonths(1).toString()))
                .andExpect(jsonPath(levelPath(level) + ".upcoming.amountCents").value(3740))
                .andExpect(jsonPath(levelPath(level) + ".upcoming.createdByName").value("Tasude Admin"))
                .andExpect(jsonPath(levelPath(level) + ".upcoming.createdAt").isNotEmpty())
                .andExpect(jsonPath(levelPath(level) + ".history.length()").value(2));
    }

    @Test
    void upcoming_isTheNearestScheduledChange() throws Exception {
        save(thisMonth.plusMonths(3), fee(level, 900));
        save(thisMonth.plusMonths(1), fee(level, 700));

        getFees()
                .andExpect(jsonPath(levelPath(level) + ".upcoming.validFrom").value(thisMonth.plusMonths(1).toString()))
                .andExpect(jsonPath(levelPath(level) + ".upcoming.amountCents").value(700));
    }

    @Test
    void save_currentMonthNotYetCharged_becomesTheCurrentFee() throws Exception {
        save(thisMonth, fee(level, 2340))
                .andExpect(status().isOk())
                .andExpect(jsonPath(levelPath(level) + ".currentAmountCents").value(2340))
                .andExpect(jsonPath(levelPath(level) + ".upcoming").value(contains(nullValue())));
    }

    @Test
    void save_zero_isAllowedAndShownInHistory() throws Exception {
        pastFee(level, thisMonth.minusMonths(2), 500);

        save(thisMonth, fee(level, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath(levelPath(level) + ".currentAmountCents").value(0))
                .andExpect(jsonPath(levelPath(level) + ".history[0].amountCents").value(0));
    }

    @Test
    void save_someLevels_othersKeepTheirFees() throws Exception {
        pastFee(otherLevel, thisMonth.minusMonths(1), 250);

        save(thisMonth.plusMonths(1), fee(level, 600)).andExpect(status().isOk());

        assertThat(feesOf(otherLevel)).singleElement().extracting(MembershipFee::getAmountCents).isEqualTo(250L);
    }

    @Test
    void save_severalLevelsAtOnce() throws Exception {
        save(thisMonth.plusMonths(1), fee(level, 600), fee(otherLevel, 180))
                .andExpect(jsonPath(levelPath(level) + ".upcoming.amountCents").value(600))
                .andExpect(jsonPath(levelPath(otherLevel) + ".upcoming.amountCents").value(180));
    }

    @Test
    void save_sameMonthAgain_replacesTheScheduledFee() throws Exception {
        save(thisMonth.plusMonths(1), fee(level, 5000));

        save(thisMonth.plusMonths(1), fee(level, 500)).andExpect(status().isOk());

        assertThat(feesOf(level)).singleElement().satisfies(fee -> {
            assertThat(fee.getAmountCents()).isEqualTo(500);
            assertThat(fee.getValidFrom()).isEqualTo(thisMonth.plusMonths(1).atDay(1));
            assertThat(fee.getCreatedBy().getId()).isEqualTo(admin.getId());
        });
    }

    @Test
    void save_inactiveLevel_isAllowed() throws Exception {
        MembershipLevel retired = level("Mahavõetud", false);

        save(thisMonth.plusMonths(1), fee(retired, 100)).andExpect(status().isOk());
    }

    @Test
    void save_pastMonth_returns403AndSavesNothing() throws Exception {
        save(thisMonth.minusMonths(1), fee(level, 500))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(139));

        assertThat(feesOf(level)).isEmpty();
    }

    @Test
    void save_currentMonthAlreadyChargedForTheLevel_returns403AndSavesNoLevel() throws Exception {
        pastFee(otherLevel, thisMonth.minusMonths(1), 500);
        chargeThisMonth(otherLevel, 500);

        save(thisMonth, fee(level, 700), fee(otherLevel, 900))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(140));

        assertThat(feesOf(level)).isEmpty();
        assertThat(feesOf(otherLevel)).singleElement().extracting(MembershipFee::getAmountCents).isEqualTo(500L);
    }

    @Test
    void save_currentMonthChargedForAnotherLevelOnly_isAllowed() throws Exception {
        chargeThisMonth(otherLevel, 500);

        save(thisMonth, fee(level, 700)).andExpect(status().isOk());
    }

    @Test
    void save_nextMonthWhileThisMonthIsCharged_isAllowed() throws Exception {
        pastFee(level, thisMonth.minusMonths(1), 500);
        chargeThisMonth(level, 500);

        save(thisMonth.plusMonths(1), fee(level, 700))
                .andExpect(status().isOk())
                .andExpect(jsonPath(levelPath(level) + ".currentAmountCents").value(500));
    }

    @Test
    void save_unknownLevel_returns404AndSavesNothing() throws Exception {
        save(thisMonth.plusMonths(1), fee(level, 700), "{\"levelId\":" + Long.MAX_VALUE + ",\"amountCents\":100}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));

        assertThat(feesOf(level)).isEmpty();
    }

    @Test
    void save_sameLevelTwice_returns400() throws Exception {
        save(thisMonth.plusMonths(1), fee(level, 700), fee(level, 800))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103))
                .andExpect(jsonPath("$.fieldErrors.levelIdsUnique").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"validFrom\":\"NEXT\",\"fees\":[]}",
            "{\"fees\":[{\"levelId\":1,\"amountCents\":100}]}",
            "{\"validFrom\":\"NEXT\"}",
            "{\"validFrom\":\"NEXT\",\"fees\":[{\"levelId\":1,\"amountCents\":-1}]}",
            "{\"validFrom\":\"NEXT\",\"fees\":[{\"levelId\":1,\"amountCents\":1000001}]}",
            "{\"validFrom\":\"NEXT\",\"fees\":[{\"levelId\":1}]}",
            "{\"validFrom\":\"NEXT\",\"fees\":[{\"amountCents\":100}]}",
            "{\"validFrom\":\"NEXT\",\"fees\":[null]}"
    })
    void save_invalidFields_returns400(String body) throws Exception {
        sendJson(post("/api/admin/fees"), adminSession, body.replace("NEXT", thisMonth.plusMonths(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "2026-12-01", "detsember",})
    void save_malformedMonth_returns400(String month) throws Exception {
        sendJson(post("/api/admin/fees"), adminSession,
                "{\"validFrom\":\"" + month + "\",\"fees\":[" + fee(level, 100) + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    @Test
    void save_fractionalCents_returns400() throws Exception {
        sendJson(post("/api/admin/fees"), adminSession, "{\"validFrom\":\"" + thisMonth.plusMonths(1)
                + "\",\"fees\":[{\"levelId\":" + level.getId() + ",\"amountCents\":12.5}]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void save_byMember_returns403AndSavesNothing() throws Exception {
        sendJson(post("/api/admin/fees"), testSessions.memberSession(),
                "{\"validFrom\":\"" + thisMonth.plusMonths(1) + "\",\"fees\":[" + fee(level, 1) + "]}")
                .andExpect(status().isForbidden());

        assertThat(feesOf(level)).isEmpty();
    }

    // ---- helpers ----

    private ResultActions getFees() throws Exception {
        return send(get("/api/admin/fees"), adminSession);
    }

    private ResultActions save(YearMonth validFrom, String... fees) throws Exception {
        return sendJson(post("/api/admin/fees"), adminSession,
                "{\"validFrom\":\"" + validFrom + "\",\"fees\":[" + String.join(",", fees) + "]}");
    }

    private static String fee(MembershipLevel level, long amountCents) {
        return "{\"levelId\":" + level.getId() + ",\"amountCents\":" + amountCents + "}";
    }

    private MembershipLevel level(String name, boolean active) {
        MembershipLevel newLevel = new MembershipLevel();
        newLevel.setName(name + " " + UUID.randomUUID());
        newLevel.setSortOrder(100);
        newLevel.setActive(active);
        newLevel.setCreatedAt(OffsetDateTime.now());
        entityManager.persist(newLevel);
        entityManager.flush();
        return newLevel;
    }

    // Past months cannot be set through the API: written directly, as if saved back then
    private void pastFee(MembershipLevel feeLevel, YearMonth validFrom, long amountCents) {
        MembershipFee fee = new MembershipFee();
        fee.setLevel(entityManager.find(MembershipLevel.class, feeLevel.getId()));
        fee.setValidFrom(validFrom.atDay(1));
        fee.setAmountCents(amountCents);
        fee.setCreatedBy(entityManager.find(Member.class, admin.getId()));
        fee.setCreatedAt(OffsetDateTime.now());
        entityManager.persist(fee);
        forget();
    }

    // What the monthly fee job (B23) will write: one charge of a member on this level for this month
    private void chargeThisMonth(MembershipLevel chargedLevel, long amountCents) {
        Member member = testSessions.createMember(MemberRoleEnum.MEMBER);
        entityManager.createNativeQuery("""
                        insert into membership_charge (member_id, month, level_id, amount_cents, status, created_at)
                        values (:memberId, :month, :levelId, :amountCents, 'CHARGED', now())""")
                .setParameter("memberId", member.getId())
                .setParameter("month", thisMonth.atDay(1))
                .setParameter("levelId", chargedLevel.getId())
                .setParameter("amountCents", amountCents)
                .executeUpdate();
        forget();
    }

    private List<MembershipFee> feesOf(MembershipLevel feeLevel) {
        return entityManager.createQuery(
                        "select f from MembershipFee f join fetch f.createdBy where f.level.id = :id order by f.validFrom",
                        MembershipFee.class)
                .setParameter("id", feeLevel.getId()).getResultList();
    }

    private static String levelPath(MembershipLevel someLevel) {
        return "$[?(@.levelId == " + someLevel.getId() + ")]";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        ResultActions result = mockMvc.perform(request.with(csrf()).session(session));
        forget();
        return result;
    }

    private ResultActions sendJson(MockHttpServletRequestBuilder request, MockHttpSession session, String body)
            throws Exception {
        return send(request.contentType(MediaType.APPLICATION_JSON).content(body), session);
    }

    // All requests share the test transaction: write and forget, as a commit would between real requests
    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
