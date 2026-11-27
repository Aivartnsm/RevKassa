package ee.revkassa.controller.admin.event;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.event.EventStatusEnum;
import ee.revkassa.persistence.eventparticipant.EventParticipant;
import ee.revkassa.persistence.eventparticipant.EventParticipantStatusEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B30: an event's budget lines and their summaries through the real security chain and PostgreSQL. The dev data has
 * budget lines of its own, so every test works on a new event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminEventBudgetFlowTest {

    private static final String DRINKS = "$.categories[?(@.category == 'DRINKS')]";
    private static final String FOOD = "$.categories[?(@.category == 'FOOD')]";

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
    private Long eventId;

    @BeforeEach
    void createEvent() throws Exception {
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        adminSession = testSessions.passwordSession(admin);
        eventId = newEvent("Jõulupidu");
    }

    // ---- reading ----

    @Test
    void budget_withoutLines_listsEveryCategoryInOrderWithZeros() throws Exception {
        budget()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[*].category")
                        .value(contains("DRINKS", "SNACKS", "FOOD", "ENTERTAINMENT", "VENUE", "DECORATION", "OTHER")))
                .andExpect(jsonPath("$.categories[0].lines.length()").value(0))
                .andExpect(jsonPath("$.plannedCents").value(0))
                .andExpect(jsonPath("$.actualCents").value(0))
                .andExpect(jsonPath("$.missingActualCount").value(0))
                .andExpect(jsonPath("$.plannedResultCents").value(0))
                .andExpect(jsonPath("$.actualResultCents").value(0));
    }

    @Test
    void summaries_withMissingActualAmounts_countOnlyFilledActuals() throws Exception {
        participant(1500, EventParticipantStatusEnum.ACTIVE);
        participant(800, EventParticipantStatusEnum.ACTIVE);
        participant(2000, EventParticipantStatusEnum.REMOVED);
        long entriesBefore = accountEntryCount();
        createLine("DRINKS", "Saku Originaal, 3 kasti", 9000, null).andExpect(status().isCreated());
        createLine("DRINKS", "Limonaad", 1000, 1200L).andExpect(status().isCreated());
        createLine("FOOD", "Õhtusöök", 30000, 28000L).andExpect(status().isCreated());

        budget()
                .andExpect(jsonPath(DRINKS + ".plannedCents").value(10000))
                .andExpect(jsonPath(DRINKS + ".actualCents").value(1200))
                .andExpect(jsonPath(FOOD + ".plannedCents").value(30000))
                .andExpect(jsonPath(FOOD + ".actualCents").value(28000))
                .andExpect(jsonPath("$.plannedCents").value(40000))
                .andExpect(jsonPath("$.actualCents").value(29200))
                .andExpect(jsonPath("$.missingActualCount").value(1))
                .andExpect(jsonPath("$.feeIncomeCents").value(2300))
                .andExpect(jsonPath("$.plannedResultCents").value(2300 - 40000))
                .andExpect(jsonPath("$.actualResultCents").value(2300 - 29200));
        // The same budget in the event detail, and both results on the list card
        perform(get("/api/admin/events/" + eventId))
                .andExpect(jsonPath("$.budget.plannedResultCents").value(2300 - 40000))
                .andExpect(jsonPath("$.budget.missingActualCount").value(1));
        perform(get("/api/admin/events"))
                .andExpect(jsonPath("$[?(@.eventId == " + eventId + ")].plannedResultCents").value(2300 - 40000))
                .andExpect(jsonPath("$[?(@.eventId == " + eventId + ")].actualResultCents").value(2300 - 29200));
        // A planning sheet: no balance rows
        assertThat(accountEntryCount()).isEqualTo(entriesBefore);
    }

    @Test
    void list_eventWithoutBudgetLines_resultIsTheFeeIncome() throws Exception {
        participant(1500, EventParticipantStatusEnum.ACTIVE);

        perform(get("/api/admin/events"))
                .andExpect(jsonPath("$[?(@.eventId == " + eventId + ")].plannedResultCents").value(1500))
                .andExpect(jsonPath("$[?(@.eventId == " + eventId + ")].actualResultCents").value(1500));
    }

    @Test
    void budget_unknownEvent_returns404() throws Exception {
        perform(get("/api/admin/events/" + Long.MAX_VALUE + "/budget-lines"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    // ---- creating ----

    @Test
    void create_goesLastInCategory_defaultsToManual_andTrimsTexts() throws Exception {
        createLine("DRINKS", "Esimene", 100, null).andExpect(status().isCreated());

        create("""
                {"category":"DRINKS","description":"  Teine  ","plannedCents":200,"note":"   "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath(DRINKS + ".lines[*].description").value(contains("Esimene", "Teine")))
                .andExpect(jsonPath(DRINKS + ".lines[1].source").value("MANUAL"))
                .andExpect(jsonPath(DRINKS + ".lines[1].note").value(contains((Object) null)))
                .andExpect(jsonPath(DRINKS + ".lines[1].actualCents").value(contains((Object) null)));
    }

    @Test
    void create_acceptedAiSuggestion_keepsSourceAi() throws Exception {
        create("""
                {"category":"SNACKS","description":"Piparkoogid","plannedCents":2500,"source":"AI"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categories[?(@.category == 'SNACKS')].lines[0].source").value("AI"));
    }

    @Test
    void create_invalidFields_return400() throws Exception {
        create("{\"description\":\"Kategooriata\",\"plannedCents\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103))
                .andExpect(jsonPath("$.fieldErrors.category").exists());
        create("{\"category\":\"FOOD\",\"description\":\"  \",\"plannedCents\":100}").andExpect(status().isBadRequest());
        create("{\"category\":\"FOOD\",\"description\":\"" + "x".repeat(201) + "\",\"plannedCents\":100}")
                .andExpect(status().isBadRequest());
        create("{\"category\":\"FOOD\",\"description\":\"Summata\"}").andExpect(status().isBadRequest());
        create("{\"category\":\"FOOD\",\"description\":\"Negatiivne\",\"plannedCents\":-1}").andExpect(status().isBadRequest());
        create("{\"category\":\"FOOD\",\"description\":\"Suur\",\"plannedCents\":1000001}").andExpect(status().isBadRequest());
        create("{\"category\":\"FOOD\",\"description\":\"Tegelik\",\"plannedCents\":1,\"actualCents\":-5}")
                .andExpect(status().isBadRequest());
        create("{\"category\":\"FOOD\",\"description\":\"Märkus\",\"plannedCents\":1,\"note\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
        create("{\"category\":\"BOATS\",\"description\":\"Paat\",\"plannedCents\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    @Test
    void create_byMember_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/events/" + eventId + "/budget-lines").with(csrf())
                        .session(testSessions.memberSession()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FOOD\",\"description\":\"Toit\",\"plannedCents\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/events/" + eventId + "/budget-lines").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FOOD\",\"description\":\"Toit\",\"plannedCents\":100}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- changing ----

    @Test
    void update_fillsActualAmount_andKeepsSource() throws Exception {
        Long lineId = lineId(create("""
                {"category":"SNACKS","description":"Piparkoogid","plannedCents":2500,"source":"AI"}"""));

        updateLine(lineId, "SNACKS", "Piparkoogid", 2500, 2350L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualCents").value(2350))
                .andExpect(jsonPath("$.missingActualCount").value(0))
                .andExpect(jsonPath("$.categories[?(@.category == 'SNACKS')].lines[0].source").value("AI"));
    }

    @Test
    void update_toAnotherCategory_goesLastThere() throws Exception {
        createLine("FOOD", "Supp", 100, null).andExpect(status().isCreated());
        Long moved = lineId(createLine("DRINKS", "Kohv", 200, null));

        updateLine(moved, "FOOD", "Kohv ja kook", 300, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath(DRINKS + ".lines[*]").isEmpty())
                .andExpect(jsonPath(FOOD + ".lines[*].description").value(contains("Supp", "Kohv ja kook")));
    }

    @Test
    void update_lineOfAnotherEvent_returns404() throws Exception {
        Long otherEvent = newEvent("Teine üritus");
        Long foreignLine = lineId(perform(post("/api/admin/events/" + otherEvent + "/budget-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"FOOD\",\"description\":\"Võõras\",\"plannedCents\":100}")));

        updateLine(foreignLine, "FOOD", "Üle võetud", 1, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
        perform(delete("/api/admin/events/" + eventId + "/budget-lines/" + foreignLine)).andExpect(status().isNotFound());
    }

    @Test
    void delete_removesTheLine() throws Exception {
        Long lineId = lineId(createLine("VENUE", "Saal", 5000, null));

        perform(delete("/api/admin/events/" + eventId + "/budget-lines/" + lineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedCents").value(0));
        perform(delete("/api/admin/events/" + eventId + "/budget-lines/" + lineId)).andExpect(status().isNotFound());
    }

    // ---- read-only states ----

    @Test
    void cancelledEvent_budgetIsReadOnly() throws Exception {
        Long lineId = lineId(createLine("VENUE", "Saal", 5000, null));
        setStatus(EventStatusEnum.CANCELLED);

        createLine("FOOD", "Hiljem", 100, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(148));
        updateLine(lineId, "VENUE", "Saal", 5000, 5000L).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(148));
        perform(delete("/api/admin/events/" + eventId + "/budget-lines/" + lineId))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(148));
        budget().andExpect(status().isOk()).andExpect(jsonPath("$.plannedCents").value(5000));
    }

    @Test
    void closedEvent_budgetStaysEditable_forReceiptsAfterClosing() throws Exception {
        Long lineId = lineId(createLine("VENUE", "Saal", 5000, null));
        setStatus(EventStatusEnum.CLOSED);

        updateLine(lineId, "VENUE", "Saal", 5000, 4500L).andExpect(status().isOk()).andExpect(jsonPath("$.actualCents").value(4500));
        createLine("OTHER", "Taksod", 0, 2000L).andExpect(status().isCreated());
    }

    // ---- helpers ----

    private Long newEvent(String name) throws Exception {
        String json = perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"startsAt\":\"2030-12-12T19:00:00+02:00\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.eventId")).longValue();
    }

    private void participant(long feeCents, EventParticipantStatusEnum status) {
        Member member = testSessions.createMember(MemberRoleEnum.MEMBER);
        EventParticipant participant = new EventParticipant();
        participant.setEvent(entityManager.getReference(Event.class, eventId));
        participant.setMember(member);
        participant.setLevel(member.getLevel());
        participant.setFeeCents(feeCents);
        participant.setStatus(status);
        participant.setAddedBy(entityManager.getReference(Member.class, admin.getId()));
        participant.setAddedAt(OffsetDateTime.now(clock));
        entityManager.persist(participant);
        forget();
    }

    private void setStatus(EventStatusEnum status) {
        entityManager.find(Event.class, eventId).setStatus(status);
        forget();
    }

    private long accountEntryCount() {
        return entityManager.createQuery("select count(e) from AccountEntry e", Long.class).getSingleResult();
    }

    private ResultActions budget() throws Exception {
        return perform(get("/api/admin/events/" + eventId + "/budget-lines"));
    }

    private ResultActions createLine(String category, String description, long plannedCents, Long actualCents) throws Exception {
        return create("{\"category\":\"%s\",\"description\":\"%s\",\"plannedCents\":%d,\"actualCents\":%s}"
                .formatted(category, description, plannedCents, actualCents));
    }

    private ResultActions create(String json) throws Exception {
        return perform(post("/api/admin/events/" + eventId + "/budget-lines").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updateLine(Long lineId, String category, String description, long plannedCents, Long actualCents)
            throws Exception {
        return perform(put("/api/admin/events/" + eventId + "/budget-lines/" + lineId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"%s\",\"description\":\"%s\",\"plannedCents\":%d,\"actualCents\":%s}"
                        .formatted(category, description, plannedCents, actualCents)));
    }

    // The newest line: the highest id in the returned budget
    private static Long lineId(ResultActions createResult) throws Exception {
        List<Number> ids = JsonPath.read(createResult.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.categories[*].lines[*].lineId");
        return ids.stream().mapToLong(Number::longValue).max().orElseThrow();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request.with(csrf()).session(adminSession));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        forget();
        return result;
    }

    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
