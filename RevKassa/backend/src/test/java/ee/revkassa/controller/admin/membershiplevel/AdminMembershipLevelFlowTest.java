package ee.revkassa.controller.admin.membershiplevel;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.testdata.FakeMailer;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B27: membership levels (list, add, rename, reorder, deactivate, delete) and the default level of a new member,
 * through the real security chain and PostgreSQL. Every level name gets a random suffix: names are unique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class, FakeMailer.Config.class})
class AdminMembershipLevelFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private FakeMailer fakeMailer;
    @Autowired
    private EntityManager entityManager;

    private Member admin;
    private MockHttpSession adminSession;
    private String suffix;

    @BeforeEach
    void createAdmin() throws Exception {
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        adminSession = testSessions.passwordSession(admin);
        suffix = " " + UUID.randomUUID().toString().substring(0, 8);
        fakeMailer.clear();
        forget();
    }

    // ---- list ----

    @Test
    void list_isInDisplayOrderWithActiveMemberCountAndDeletable() throws Exception {
        long levelId = create("Loendatav" + suffix);
        memberOnLevel(levelId);
        Member inactive = memberOnLevel(levelId);
        inactive.setActive(false);
        long unusedId = create("Kasutamata" + suffix);
        forget();

        String json = send(get("/api/admin/levels"), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath(levelPath(levelId) + ".memberCount").value(1))
                .andExpect(jsonPath(levelPath(levelId) + ".deletable").value(false))
                .andExpect(jsonPath(levelPath(unusedId) + ".memberCount").value(0))
                .andExpect(jsonPath(levelPath(unusedId) + ".deletable").value(true))
                .andReturn().getResponse().getContentAsString();

        List<Integer> sortOrders = JsonPath.read(json, "$[*].sortOrder");
        assertThat(sortOrders).isSorted();
    }

    @Test
    void list_byMember_returns403() throws Exception {
        send(get("/api/admin/levels"), testSessions.memberSession())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void list_byAdminWithPin_returns403() throws Exception {
        send(get("/api/admin/levels"), testSessions.pinSession(MemberRoleEnum.ADMIN))
                .andExpect(status().isForbidden());
    }

    // ---- create ----

    @Test
    void create_isActiveAndGoesLast() throws Exception {
        int maxSortOrder = entityManager.createQuery("select max(l.sortOrder) from MembershipLevel l", Integer.class)
                .getSingleResult();

        sendJson(post("/api/admin/levels"), adminSession, "{\"name\":\"  Auliige pluss" + suffix + " \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Auliige pluss" + suffix))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sortOrder").value(maxSortOrder + 1))
                .andExpect(jsonPath("$.memberCount").value(0))
                .andExpect(jsonPath("$.deletable").value(true));
    }

    @Test
    void create_nameTakenInOtherCase_returns403() throws Exception {
        create("Kordus" + suffix);

        sendJson(post("/api/admin/levels"), adminSession, "{\"name\":\"KORDUS" + suffix.toUpperCase() + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(135));
    }

    @Test
    void create_seededNameTaken_returns403() throws Exception {
        sendJson(post("/api/admin/levels"), adminSession, "{\"name\":\"täisliige\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(135));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"   \"}", "{\"name\":\"123456789012345678901234567890123456789012345678901\"}"})
    void create_invalidName_returns400(String body) throws Exception {
        sendJson(post("/api/admin/levels"), adminSession, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));
    }

    @Test
    void create_byMember_returns403AndCreatesNothing() throws Exception {
        sendJson(post("/api/admin/levels"), testSessions.memberSession(), "{\"name\":\"Kuri" + suffix + "\"}")
                .andExpect(status().isForbidden());

        assertThat(levelCountNamed("Kuri" + suffix)).isZero();
    }

    // ---- update ----

    @Test
    void update_renamesReordersAndDeactivates() throws Exception {
        long levelId = create("Vana nimi" + suffix);

        update(levelId, "Uus nimi" + suffix, 0, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Uus nimi" + suffix))
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.active").value(false));

        MembershipLevel saved = entityManager.find(MembershipLevel.class, levelId);
        assertThat(saved.getName()).isEqualTo("Uus nimi" + suffix);
        assertThat(saved.getActive()).isFalse();
    }

    @Test
    void update_nameOfAnotherLevel_returns403() throws Exception {
        create("Esimene" + suffix);
        long secondId = create("Teine" + suffix);

        update(secondId, "esimene" + suffix, 10, true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(135));
    }

    @Test
    void update_ownNameInOtherCase_isAllowed() throws Exception {
        long levelId = create("Suurtäht" + suffix);

        update(levelId, "SUURTÄHT" + suffix, 10, true).andExpect(status().isOk());
    }

    @Test
    void update_unknownLevel_returns404() throws Exception {
        update(Long.MAX_VALUE, "Keegi" + suffix, 1, true)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    void deactivate_membersKeepTheLevelButNewMembersCannotGetIt() throws Exception {
        long levelId = create("Mahavõetav" + suffix);
        Member onLevel = memberOnLevel(levelId);
        forget();

        update(levelId, "Mahavõetav" + suffix, 10, false).andExpect(status().isOk());

        assertThat(entityManager.find(Member.class, onLevel.getId()).getLevel().getId()).isEqualTo(levelId);
        createMember("{\"displayName\":\"Uus\",\"email\":\"" + newEmail() + "\",\"role\":\"MEMBER\",\"feeExempt\":false,"
                + "\"levelId\":" + levelId + "}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(138));
    }

    @Test
    void deactivate_lastActiveLevel_returns403AndKeepsItActive() throws Exception {
        long levelId = create("Viimane" + suffix);
        deactivateAllLevelsExcept(levelId);

        update(levelId, "Viimane" + suffix, 10, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(137));

        assertThat(entityManager.find(MembershipLevel.class, levelId).getActive()).isTrue();
    }

    @Test
    void lastActiveLevel_canStillBeRenamed() throws Exception {
        long levelId = create("Ainus" + suffix);
        deactivateAllLevelsExcept(levelId);

        update(levelId, "Ainus ümber" + suffix, 10, true).andExpect(status().isOk());
    }

    // ---- default level of a new member ----

    @Test
    void newMemberWithoutLevel_getsFirstActiveLevelInDisplayOrder() throws Exception {
        long firstId = create("Esimesena" + suffix);
        update(firstId, "Esimesena" + suffix, 0, true);
        moveAllOtherLevelsAfter(firstId);

        String json = createMember("{\"displayName\":\"Vaikimisi\",\"email\":\"" + newEmail() + "\",\"role\":\"MEMBER\","
                + "\"feeExempt\":false}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.levelId").value(Math.toIntExact(firstId)))
                .andReturn().getResponse().getContentAsString();

        long memberId = ((Number) JsonPath.read(json, "$.member.memberId")).longValue();
        assertThat(entityManager.find(Member.class, memberId).getLevel().getId()).isEqualTo(firstId);
    }

    @Test
    void newMemberWithoutLevel_skipsDeactivatedFirstLevel() throws Exception {
        long retiredId = create("Mahavõetud esimene" + suffix);
        long nextId = create("Järgmine" + suffix);
        update(retiredId, "Mahavõetud esimene" + suffix, 0, false);
        update(nextId, "Järgmine" + suffix, 1, true);
        moveAllOtherLevelsAfter(retiredId, nextId);

        createMember("{\"displayName\":\"Vaikimisi\",\"email\":\"" + newEmail() + "\",\"role\":\"MEMBER\",\"feeExempt\":false}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.levelId").value(Math.toIntExact(nextId)));
    }

    // ---- delete ----

    @Test
    void delete_unusedLevel_removesIt() throws Exception {
        long levelId = create("Kustutatav" + suffix);

        send(delete("/api/admin/levels/" + levelId), adminSession).andExpect(status().isNoContent());

        assertThat(entityManager.find(MembershipLevel.class, levelId)).isNull();
    }

    @Test
    void delete_levelOfAMember_returns403AndKeepsIt() throws Exception {
        long levelId = create("Liikmega" + suffix);
        memberOnLevel(levelId);
        forget();

        send(delete("/api/admin/levels/" + levelId), adminSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(136));

        assertThat(entityManager.find(MembershipLevel.class, levelId)).isNotNull();
    }

    @Test
    void delete_levelOfAnInactiveMemberOnly_returns403() throws Exception {
        long levelId = create("Endise liikmega" + suffix);
        memberOnLevel(levelId).setActive(false);
        forget();

        send(delete("/api/admin/levels/" + levelId), adminSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(136));
    }

    @Test
    void delete_levelWithAMonthlyFee_returns403() throws Exception {
        long levelId = create("Tasuga" + suffix);
        entityManager.createNativeQuery("""
                        insert into membership_fee (level_id, amount_cents, valid_from, created_by, created_at)
                        values (:levelId, 500, date '2027-01-01', :adminId, now())""")
                .setParameter("levelId", levelId)
                .setParameter("adminId", admin.getId())
                .executeUpdate();

        send(delete("/api/admin/levels/" + levelId), adminSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(136));
    }

    @Test
    void delete_deactivatedUnusedLevel_isAllowed() throws Exception {
        long levelId = create("Mahavõetud kustutatav" + suffix);
        update(levelId, "Mahavõetud kustutatav" + suffix, 10, false);

        send(delete("/api/admin/levels/" + levelId), adminSession).andExpect(status().isNoContent());
    }

    @Test
    void delete_lastActiveLevel_returns403() throws Exception {
        long levelId = create("Viimane kustutatav" + suffix);
        deactivateAllLevelsExcept(levelId);

        send(delete("/api/admin/levels/" + levelId), adminSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(137));
    }

    @Test
    void delete_unknownLevel_returns404() throws Exception {
        send(delete("/api/admin/levels/" + Long.MAX_VALUE), adminSession)
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_byMember_returns403AndKeepsLevel() throws Exception {
        long levelId = create("Kaitstud" + suffix);

        send(delete("/api/admin/levels/" + levelId), testSessions.memberSession())
                .andExpect(status().isForbidden());

        assertThat(entityManager.find(MembershipLevel.class, levelId)).isNotNull();
    }

    // ---- helpers ----

    private long create(String name) throws Exception {
        String json = sendJson(post("/api/admin/levels"), adminSession, "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.levelId")).longValue();
    }

    private ResultActions update(long levelId, String name, int sortOrder, boolean active) throws Exception {
        return sendJson(put("/api/admin/levels/" + levelId), adminSession,
                "{\"name\":\"" + name + "\",\"sortOrder\":" + sortOrder + ",\"active\":" + active + "}");
    }

    private ResultActions createMember(String body) throws Exception {
        return sendJson(post("/api/admin/members"), adminSession, body);
    }

    private Member memberOnLevel(long levelId) {
        Member member = testSessions.createMember(MemberRoleEnum.MEMBER);
        member.setLevel(entityManager.find(MembershipLevel.class, levelId));
        entityManager.flush();
        return member;
    }

    private static String newEmail() {
        return "taseme-liige-" + UUID.randomUUID() + "@example.org";
    }

    // Inside the test transaction only: rolled back afterwards like everything else
    private void deactivateAllLevelsExcept(long levelId) {
        entityManager.createQuery("update MembershipLevel l set l.active = false where l.id <> :id")
                .setParameter("id", levelId)
                .executeUpdate();
        forget();
    }

    private void moveAllOtherLevelsAfter(Long... levelIds) {
        entityManager.createQuery("update MembershipLevel l set l.sortOrder = l.sortOrder + 100 where l.id not in :ids")
                .setParameter("ids", List.of(levelIds))
                .executeUpdate();
        forget();
    }

    private long levelCountNamed(String name) {
        return entityManager.createQuery("select count(l) from MembershipLevel l where l.name = :name", Long.class)
                .setParameter("name", name).getSingleResult();
    }

    private static String levelPath(long levelId) {
        return "$[?(@.levelId == " + levelId + ")]";
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
