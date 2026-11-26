package ee.revkassa.controller.admin.event;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.event.EventStatusEnum;
import ee.revkassa.persistence.eventparticipant.EventParticipant;
import ee.revkassa.persistence.eventparticipant.EventParticipantStatusEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B28: events and their fee tables through the real security chain and PostgreSQL. The dev data has events and levels
 * of its own, so assertions pick out this test's rows by id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminEventFlowTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

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
    private MembershipLevel full;
    private MembershipLevel youth;

    @BeforeEach
    void createAdminAndLevels() throws Exception {
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        admin.setDisplayName("Ürituse Admin");
        adminSession = testSessions.passwordSession(admin);
        full = level("Täis", true);
        youth = level("Noor", true);
        forget();
    }

    // ---- creating ----

    @Test
    void create_returns201PlannedWithSignupClosedAndAllFeesZero() throws Exception {
        perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"  Jõulupidu  ","startsAt":"2030-12-12T19:00:00+02:00","location":" Klubiruum ",
                 "description":"   "}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").isNumber())
                .andExpect(jsonPath("$.name").value("Jõulupidu"))
                .andExpect(jsonPath("$.startsAt").value("2030-12-12T19:00:00+02:00"))
                .andExpect(jsonPath("$.location").value("Klubiruum"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.signupOpen").value(false))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.createdByName").value("Ürituse Admin"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.participantCount").value(0))
                .andExpect(jsonPath("$.feeIncomeCents").value(0))
                .andExpect(jsonPath(feeOf(full) + ".amountCents").value(0))
                .andExpect(jsonPath(feeOf(youth) + ".levelName").value(youth.getName()));
    }

    @Test
    void create_startInThePast_isAllowed() throws Exception {
        createEvent("Möödunud", OffsetDateTime.now(clock).minusDays(30)).andExpect(status().isCreated());
    }

    @Test
    void create_invalidFields_return400() throws Exception {
        createBody("{\"name\":\"  \",\"startsAt\":\"2030-01-01T10:00:00+02:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        createBody("{\"name\":\"" + "x".repeat(101) + "\",\"startsAt\":\"2030-01-01T10:00:00+02:00\"}")
                .andExpect(status().isBadRequest());
        createBody("{\"name\":\"Aeg puudu\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startsAt").exists());
        createBody("{\"name\":\"Pikk\",\"startsAt\":\"2030-01-01T10:00:00+02:00\",\"location\":\"" + "x".repeat(101) + "\"}")
                .andExpect(status().isBadRequest());
        createBody("{\"name\":\"Pikk\",\"startsAt\":\"2030-01-01T10:00:00+02:00\",\"description\":\"" + "x".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest());
        createBody("{\"name\":\"Vale aeg\",\"startsAt\":\"homme\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    @Test
    void create_byMember_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/events").with(csrf()).session(testSessions.memberSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Liikme pidu\",\"startsAt\":\"2030-01-01T10:00:00+02:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/events").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Csrf\",\"startsAt\":\"2030-01-01T10:00:00+02:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- reading ----

    @Test
    void list_plannedSoonestFirstThenOthersNewestFirst() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Long plannedLater = eventId(createEvent("Hiljem", now.plusDays(20)));
        Long plannedSooner = eventId(createEvent("Varem", now.plusDays(10)));
        Long closedOld = eventId(createEvent("Vana", now.minusDays(40)));
        Long cancelledNewer = eventId(createEvent("Uuem", now.minusDays(5)));
        setStatus(closedOld, EventStatusEnum.CLOSED);
        setStatus(cancelledNewer, EventStatusEnum.CANCELLED);

        String json = perform(get("/api/admin/events")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Integer> ids = JsonPath.read(json, "$[*].eventId");
        List<Long> ours = ids.stream().map(Integer::longValue)
                .filter(id -> List.of(plannedLater, plannedSooner, closedOld, cancelledNewer).contains(id)).toList();
        assertThat(ours).containsExactly(plannedSooner, plannedLater, cancelledNewer, closedOld);
        // Every planned event comes before every closed or cancelled one
        List<String> statuses = JsonPath.read(json, "$[*].status");
        assertThat(statuses.indexOf("PLANNED") < 0 || statuses.lastIndexOf("PLANNED") < firstNotPlanned(statuses)).isTrue();
    }

    @Test
    void detail_countsOnlyActiveParticipantsAndTheirFees() throws Exception {
        Long eventId = eventId(createEvent("Osalejatega", OffsetDateTime.now(clock).plusDays(3)));
        participant(eventId, 1500, EventParticipantStatusEnum.ACTIVE);
        participant(eventId, 800, EventParticipantStatusEnum.ACTIVE);
        participant(eventId, 2000, EventParticipantStatusEnum.REMOVED);

        perform(get("/api/admin/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.feeIncomeCents").value(2300));
        perform(get("/api/admin/events"))
                .andExpect(jsonPath("$[?(@.eventId == " + eventId + ")].participantCount").value(2))
                .andExpect(jsonPath("$[?(@.eventId == " + eventId + ")].feeIncomeCents").value(2300));
    }

    @Test
    void detail_unknownEvent_returns404() throws Exception {
        perform(get("/api/admin/events/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    void detail_byMember_returns403() throws Exception {
        Long eventId = eventId(createEvent("Salajane", OffsetDateTime.now(clock).plusDays(3)));

        mockMvc.perform(get("/api/admin/events/" + eventId).session(testSessions.memberSession()))
                .andExpect(status().isForbidden());
    }

    // ---- changing ----

    @Test
    void update_savesFieldsAndSignupAndRaisesVersion() throws Exception {
        Long eventId = eventId(createEvent("Algne", OffsetDateTime.now(clock).plusDays(3)));

        updateEvent(eventId, "Muudetud", "2030-05-01T18:00:00+03:00", true, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Muudetud"))
                .andExpect(jsonPath("$.startsAt").value("2030-05-01T18:00:00+03:00"))
                .andExpect(jsonPath("$.signupOpen").value(true))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void update_staleVersion_returns409AndKeepsEvent() throws Exception {
        Long eventId = eventId(createEvent("Kaks adminit", OffsetDateTime.now(clock).plusDays(3)));
        updateEvent(eventId, "Esimene", "2030-05-01T18:00:00+03:00", false, 0).andExpect(status().isOk());

        updateEvent(eventId, "Teine", "2030-05-01T18:00:00+03:00", false, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(105));

        assertThat(entityManager.find(Event.class, eventId).getName()).isEqualTo("Esimene");
    }

    @Test
    void update_closedOrCancelledEvent_returns403() throws Exception {
        Long closed = eventId(createEvent("Suletud", OffsetDateTime.now(clock).minusDays(3)));
        Long cancelled = eventId(createEvent("Tühistatud", OffsetDateTime.now(clock).plusDays(3)));
        setStatus(closed, EventStatusEnum.CLOSED);
        setStatus(cancelled, EventStatusEnum.CANCELLED);

        updateEvent(closed, "Uus nimi", "2030-05-01T18:00:00+03:00", true, 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(148));
        updateEvent(cancelled, "Uus nimi", "2030-05-01T18:00:00+03:00", true, 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(148));
        perform(get("/api/admin/events/" + closed))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.name").value("Suletud"));
    }

    @Test
    void update_missingVersionOrSignup_returns400() throws Exception {
        Long eventId = eventId(createEvent("Puudulik", OffsetDateTime.now(clock).plusDays(3)));

        perform(put("/api/admin/events/" + eventId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"startsAt\":\"2030-05-01T18:00:00+03:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists())
                .andExpect(jsonPath("$.fieldErrors.signupOpen").exists());
    }

    @Test
    void update_unknownEvent_returns404() throws Exception {
        updateEvent(Long.MAX_VALUE, "X", "2030-05-01T18:00:00+03:00", false, 0).andExpect(status().isNotFound());
    }

    // ---- fee table ----

    @Test
    void fees_savesAmountsAndRaisesVersion() throws Exception {
        Long eventId = eventId(createEvent("Tasudega", OffsetDateTime.now(clock).plusDays(3)));

        saveFees(eventId, 0, fee(full, 1500), fee(youth, 800))
                .andExpect(status().isOk())
                .andExpect(jsonPath(feeOf(full) + ".amountCents").value(1500))
                .andExpect(jsonPath(feeOf(youth) + ".amountCents").value(800))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void fees_replaceTheWholeTable_unlistedLevelGoesBackToZero() throws Exception {
        Long eventId = eventId(createEvent("Asendus", OffsetDateTime.now(clock).plusDays(3)));
        saveFees(eventId, 0, fee(full, 1500), fee(youth, 800));

        saveFees(eventId, 1, fee(full, 2000))
                .andExpect(status().isOk())
                .andExpect(jsonPath(feeOf(full) + ".amountCents").value(2000))
                .andExpect(jsonPath(feeOf(youth) + ".amountCents").value(0));
        saveFees(eventId, 2).andExpect(jsonPath(feeOf(full) + ".amountCents").value(0));
    }

    @Test
    void fees_deactivatedLevel_isListedOnlyWithAFee() throws Exception {
        MembershipLevel former = level("Endine", false);
        MembershipLevel unused = level("Kasutamata", false);
        forget();
        Long eventId = eventId(createEvent("Endised", OffsetDateTime.now(clock).plusDays(3)));

        saveFees(eventId, 0, fee(former, 500), fee(unused, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath(feeOf(former) + ".amountCents").value(500))
                .andExpect(jsonPath(feeOf(former) + ".levelActive").value(false))
                .andExpect(jsonPath(feeOf(unused)).isEmpty());
    }

    @Test
    void fees_staleVersion_returns409AndKeepsFees() throws Exception {
        Long eventId = eventId(createEvent("Tasukonflikt", OffsetDateTime.now(clock).plusDays(3)));
        saveFees(eventId, 0, fee(full, 1500));

        saveFees(eventId, 0, fee(full, 9900))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(105));

        perform(get("/api/admin/events/" + eventId)).andExpect(jsonPath(feeOf(full) + ".amountCents").value(1500));
    }

    @Test
    void fees_afterEventEdit_needTheNewVersion() throws Exception {
        Long eventId = eventId(createEvent("Järjest", OffsetDateTime.now(clock).plusDays(3)));
        updateEvent(eventId, "Järjest", "2030-05-01T18:00:00+03:00", true, 0);

        saveFees(eventId, 0, fee(full, 1500)).andExpect(status().isConflict());
        saveFees(eventId, 1, fee(full, 1500)).andExpect(status().isOk());
    }

    @Test
    void fees_invalidRows_return400() throws Exception {
        Long eventId = eventId(createEvent("Vigased", OffsetDateTime.now(clock).plusDays(3)));

        saveFees(eventId, 0, fee(full, -1)).andExpect(status().isBadRequest());
        saveFees(eventId, 0, fee(full, 1_000_001)).andExpect(status().isBadRequest());
        saveFees(eventId, 0, fee(full, 100), fee(full, 200))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.levelIdsUnique").value("a level is listed more than once"));
        perform(put("/api/admin/events/" + eventId + "/fees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fees\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());
    }

    @Test
    void fees_unknownLevel_returns404AndKeepsFees() throws Exception {
        Long eventId = eventId(createEvent("Tundmatu tase", OffsetDateTime.now(clock).plusDays(3)));
        saveFees(eventId, 0, fee(full, 1500));

        perform(put("/api/admin/events/" + eventId + "/fees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fees\":[{\"levelId\":" + Long.MAX_VALUE + ",\"amountCents\":100}],\"version\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));

        perform(get("/api/admin/events/" + eventId)).andExpect(jsonPath(feeOf(full) + ".amountCents").value(1500));
    }

    @Test
    void fees_closedOrCancelledEvent_returns403() throws Exception {
        Long closed = eventId(createEvent("Suletud tasud", OffsetDateTime.now(clock).minusDays(3)));
        Long cancelled = eventId(createEvent("Tühistatud tasud", OffsetDateTime.now(clock).plusDays(3)));
        setStatus(closed, EventStatusEnum.CLOSED);
        setStatus(cancelled, EventStatusEnum.CANCELLED);

        saveFees(closed, 0, fee(full, 1500)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(148));
        saveFees(cancelled, 0, fee(full, 1500)).andExpect(status().isForbidden());
    }

    @Test
    void fees_byMember_returns403() throws Exception {
        Long eventId = eventId(createEvent("Liikme tasud", OffsetDateTime.now(clock).plusDays(3)));

        mockMvc.perform(put("/api/admin/events/" + eventId + "/fees").with(csrf()).session(testSessions.memberSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fees\":[],\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    // ---- dashboard ----

    @Test
    void dashboard_showsAtMostThreeUpcomingPlannedEventsSoonestFirst() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Long third = eventId(createEvent("Kolmas", now.plusMinutes(30)));
        Long first = eventId(createEvent("Esimene", now.plusMinutes(10)));
        Long second = eventId(createEvent("Teine", now.plusMinutes(20)));
        eventId(createEvent("Neljas", now.plusMinutes(40)));
        eventId(createEvent("Möödunud", now.minusMinutes(10)));
        Long cancelled = eventId(createEvent("Tühistatud", now.plusMinutes(5)));
        setStatus(cancelled, EventStatusEnum.CANCELLED);
        participant(first, 1500, EventParticipantStatusEnum.ACTIVE);

        perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingEvents.length()").value(3))
                .andExpect(jsonPath("$.upcomingEvents[0].eventId").value(first))
                .andExpect(jsonPath("$.upcomingEvents[0].name").value("Esimene"))
                .andExpect(jsonPath("$.upcomingEvents[0].participantCount").value(1))
                .andExpect(jsonPath("$.upcomingEvents[1].eventId").value(second))
                .andExpect(jsonPath("$.upcomingEvents[2].eventId").value(third))
                .andExpect(jsonPath("$.upcomingEvents[2].participantCount").value(0));
    }

    // ---- helpers ----

    private MembershipLevel level(String name, boolean active) {
        MembershipLevel level = new MembershipLevel();
        level.setName(name + " " + COUNTER.incrementAndGet());
        level.setSortOrder(900 + COUNTER.get());
        level.setActive(active);
        level.setCreatedAt(OffsetDateTime.now(clock));
        entityManager.persist(level);
        return level;
    }

    private void participant(Long eventId, long feeCents, EventParticipantStatusEnum status) {
        EventParticipant participant = new EventParticipant();
        participant.setEvent(entityManager.getReference(Event.class, eventId));
        participant.setMember(testSessions.createMember(MemberRoleEnum.MEMBER));
        participant.setLevel(entityManager.getReference(MembershipLevel.class, full.getId()));
        participant.setFeeCents(feeCents);
        participant.setStatus(status);
        participant.setAddedBy(entityManager.getReference(Member.class, admin.getId()));
        participant.setAddedAt(OffsetDateTime.now(clock));
        if (status == EventParticipantStatusEnum.REMOVED) {
            participant.setRemovedAt(OffsetDateTime.now(clock).plus(Duration.ofMinutes(1)));
        }
        entityManager.persist(participant);
        forget();
    }

    private void setStatus(Long eventId, EventStatusEnum status) {
        entityManager.find(Event.class, eventId).setStatus(status);
        forget();
    }

    private ResultActions createEvent(String name, OffsetDateTime startsAt) throws Exception {
        return createBody("{\"name\":\"" + name + "\",\"startsAt\":\"" + startsAt + "\"}");
    }

    private ResultActions createBody(String json) throws Exception {
        return perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updateEvent(Long eventId, String name, String startsAt, boolean signupOpen, long version)
            throws Exception {
        return perform(put("/api/admin/events/" + eventId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","startsAt":"%s","location":"Klubiruum","description":"Kirjeldus","signupOpen":%s,"version":%d}"""
                .formatted(name, startsAt, signupOpen, version)));
    }

    private ResultActions saveFees(Long eventId, long version, String... fees) throws Exception {
        return perform(put("/api/admin/events/" + eventId + "/fees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fees\":[" + String.join(",", fees) + "],\"version\":" + version + "}"));
    }

    private static String fee(MembershipLevel level, long amountCents) {
        return "{\"levelId\":" + level.getId() + ",\"amountCents\":" + amountCents + "}";
    }

    private static String feeOf(MembershipLevel level) {
        return "$.fees[?(@.levelId == " + level.getId() + ")]";
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request.with(csrf()).session(adminSession));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        forget();
        return result;
    }

    private static Long eventId(ResultActions createResult) throws Exception {
        return ((Number) JsonPath.read(createResult.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.eventId")).longValue();
    }

    private static int firstNotPlanned(List<String> statuses) {
        for (int i = 0; i < statuses.size(); i++) {
            if (!statuses.get(i).equals("PLANNED")) {
                return i;
            }
        }
        return statuses.size();
    }

    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
