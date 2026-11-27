package ee.revkassa.controller.admin.event;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.eventparticipant.EventParticipant;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.service.AccountEntryService;
import ee.revkassa.testdata.TestClock;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B29: participants, charging their fees, removing, closing and cancelling, through the real security chain and
 * PostgreSQL. The clock starts at 23:30 Tallinn time on 30.11, so an hour later it is already the next price period
 * in Tallinn while UTC is still on the same day. After every write the ledger invariants of DB-MODEL.md are checked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminEventParticipantFlowTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String FROM_DECEMBER = "2026-12-01";
    // 23:30 on 30.11 in Tallinn
    private static final Instant START = Instant.parse("2026-11-30T21:30:00Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(START);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TestClock clock;
    @Autowired
    private AccountEntryService accountEntryService;

    private Member admin;
    private MockHttpSession adminSession;
    private MembershipLevel full;
    private MembershipLevel youth;
    private Long eventId;

    @BeforeEach
    void createEventWithPricePeriods() throws Exception {
        clock.set(START);
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        adminSession = testSessions.passwordSession(admin);
        full = level("Täis");
        youth = level("Noor");
        forget();
        eventId = createEvent("Jõulupidu " + COUNTER.incrementAndGet());
        // From the start: full 15 €, youth 8 €; from 01.12: full 25 €, youth has no row (= 0)
        perform(put("/api/admin/events/" + eventId + "/fees").contentType(MediaType.APPLICATION_JSON).content("""
                {"periods":[{"validFrom":null,"fees":[%s,%s]},{"validFrom":"%s","fees":[%s]}],"version":0}"""
                .formatted(fee(full, 1500), fee(youth, 800), FROM_DECEMBER, fee(full, 2500))))
                .andExpect(status().isOk());
    }

    // ---- adding ----

    @Test
    void add_copiesLevelAndFeeOfTheJoinDaysPeriod_andLeavesBalanceAlone() throws Exception {
        Member early = member(full);
        Member late = member(full);
        Member lateYouth = member(youth);

        add(early).andExpect(status().isOk());
        clock.advance(Duration.ofHours(1)); // 00:30 on 01.12 in Tallinn, still 30.11 in UTC
        add(late, lateYouth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped.length()").value(0))
                .andExpect(jsonPath("$.event.participantCount").value(3))
                .andExpect(jsonPath("$.event.feeIncomeCents").value(4000))
                .andExpect(jsonPath("$.event.unchargedCount").value(3))
                .andExpect(jsonPath("$.event.unchargedCents").value(4000))
                .andExpect(jsonPath(participant(early) + ".feeCents").value(1500))
                .andExpect(jsonPath(participant(early) + ".levelName").value(full.getName()))
                .andExpect(jsonPath(participant(early) + ".status").value("ACTIVE"))
                .andExpect(jsonPath(participant(early) + ".charged").value(false))
                .andExpect(jsonPath(participant(early) + ".selfSignup").value(false))
                .andExpect(jsonPath(participant(early) + ".addedByName").value(admin.getDisplayName()))
                .andExpect(jsonPath(participant(late) + ".feeCents").value(2500))
                .andExpect(jsonPath(participant(lateYouth) + ".feeCents").value(0));

        assertThat(balanceOf(early)).isZero();
        assertThat(entriesOf(early)).isEmpty();
        assertInvariants();
    }

    @Test
    void add_skipsInactiveMembersAndActiveParticipants_andAddsDuplicatesOnce() throws Exception {
        Member joined = member(full);
        add(joined).andExpect(status().isOk());
        Member inactive = member(full);
        entityManager.find(Member.class, inactive.getId()).setActive(false);
        forget();
        Member fresh = member(full);

        add(fresh, fresh, inactive, joined)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.participantCount").value(2))
                .andExpect(jsonPath("$.skipped.length()").value(2))
                .andExpect(jsonPath("$.skipped[0].memberId").value(inactive.getId()))
                .andExpect(jsonPath("$.skipped[0].reason").value("MEMBER_NOT_ACTIVE"))
                .andExpect(jsonPath("$.skipped[1].memberId").value(joined.getId()))
                .andExpect(jsonPath("$.skipped[1].reason").value("ALREADY_PARTICIPANT"));
    }

    @Test
    void add_unknownMember_returns404AndAddsNobody() throws Exception {
        Member known = member(full);

        addIds(known.getId(), Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));

        assertThat(participantRow(known)).isNull();
    }

    @Test
    void add_invalidMemberIds_return400() throws Exception {
        addBody("{\"memberIds\":[]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value(103));
        addBody("{}").andExpect(status().isBadRequest());
        addBody("{\"memberIds\":[0]}").andExpect(status().isBadRequest());
        addBody("{\"memberIds\":[null]}").andExpect(status().isBadRequest());
        Long[] tooMany = new Long[201];
        Arrays.fill(tooMany, 1L);
        addIds(tooMany).andExpect(status().isBadRequest());
    }

    @Test
    void add_toUnknownEvent_returns404() throws Exception {
        mockMvc.perform(post("/api/admin/events/" + Long.MAX_VALUE + "/participants").with(csrf()).session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memberIds\":[" + member(full).getId() + "]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void add_byMember_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/events/" + eventId + "/participants").with(csrf())
                        .session(testSessions.memberSession()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[" + member(full).getId() + "]}"))
                .andExpect(status().isForbidden());
    }

    // ---- charging ----

    @Test
    void charge_postsEventFeeForEachFeeAboveZero_andMarksEveryoneCharged() throws Exception {
        Member paying = member(full);
        Member free = member(youth);
        entityManager.createQuery("update EventLevelFee f set f.amountCents = 0 where f.level.id = :id")
                .setParameter("id", youth.getId()).executeUpdate();
        publish();
        add(paying, free).andExpect(status().isOk());

        charge()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unchargedCount").value(0))
                .andExpect(jsonPath("$.unchargedCents").value(0))
                .andExpect(jsonPath(participant(paying) + ".charged").value(true))
                .andExpect(jsonPath(participant(free) + ".charged").value(true));

        assertThat(balanceOf(paying)).isEqualTo(-1500);
        assertThat(entriesOf(paying)).extracting(AccountEntry::getType).containsExactly(AccountEntryTypeEnum.EVENT_FEE);
        assertThat(entriesOf(free)).isEmpty();
        assertInvariants();
    }

    @Test
    void charge_again_chargesOnlyParticipantsAddedLater() throws Exception {
        Member first = member(full);
        publish();
        add(first).andExpect(status().isOk());
        charge().andExpect(status().isOk());
        Member later = member(youth);
        add(later).andExpect(status().isOk()).andExpect(jsonPath("$.event.unchargedCents").value(800));

        charge().andExpect(status().isOk());
        charge().andExpect(status().isOk());

        assertThat(balanceOf(first)).isEqualTo(-1500);
        assertThat(balanceOf(later)).isEqualTo(-800);
        assertInvariants();
    }

    @Test
    void charge_draftEvent_returns403() throws Exception {
        add(member(full)).andExpect(status().isOk());

        charge().andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(150));
    }

    @Test
    void charge_showsInMembersHistoryWithEventName() throws Exception {
        Member member = member(full);
        publish();
        add(member).andExpect(status().isOk());
        charge().andExpect(status().isOk());
        changeFee(member, 2000, "Lisaks õhtusöök").andExpect(status().isOk());
        removeParticipant(member).andExpect(status().isOk());

        assertThat(accountEntryService.getMemberEntries(member.getId(), 0).getEntries())
                .extracting(entry -> entry.getDescription() + " " + entry.getAmountCents())
                .containsExactly(
                        "Ürituse tasu tagastus · " + eventName() + " 2000",
                        "Ürituse tasu · " + eventName() + " · Lisaks õhtusöök -500",
                        "Ürituse tasu · " + eventName() + " -1500");
    }

    // ---- changing a fee ----

    @Test
    void changeFee_beforeCharging_changesOnlyTheFee() throws Exception {
        Member member = member(full);
        add(member).andExpect(status().isOk());

        changeFee(member, 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath(participant(member) + ".feeCents").value(0));

        assertThat(entriesOf(member)).isEmpty();
        assertInvariants();
    }

    @Test
    void changeFee_afterCharging_postsTheDifferenceEitherWay() throws Exception {
        Member member = member(full);
        publish();
        add(member).andExpect(status().isOk());
        charge().andExpect(status().isOk());

        changeFee(member, 2000, "Lisaks õhtusöök").andExpect(status().isOk());
        assertThat(balanceOf(member)).isEqualTo(-2000);
        changeFee(member, 500, "Lahkus enne sööki").andExpect(status().isOk());
        assertThat(balanceOf(member)).isEqualTo(-500);
        changeFee(member, 500, null).andExpect(status().isOk());

        assertThat(entriesOf(member)).extracting(entry -> entry.getType() + " " + entry.getAmountCents() + " " + entry.getNote())
                .containsExactly("EVENT_FEE -1500 null", "EVENT_FEE -500 Lisaks õhtusöök",
                        "EVENT_FEE_REVERSAL 1500 Lahkus enne sööki");
        assertInvariants();
    }

    @Test
    void changeFee_invalidOrForRemovedOrUnknown_isRefused() throws Exception {
        Member member = member(full);
        add(member).andExpect(status().isOk());
        changeFee(member, -1, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value(103));
        changeFee(member, 1_000_001, null).andExpect(status().isBadRequest());
        changeFee(member, 100, "x".repeat(501)).andExpect(status().isBadRequest());
        changeFee(member(full), 100, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value(101));
        removeParticipant(member).andExpect(status().isOk());

        changeFee(member, 100, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(152));
    }

    // ---- removing ----

    @Test
    void remove_beforeCharging_writesNothingToTheBalance() throws Exception {
        Member member = member(full);
        add(member).andExpect(status().isOk());

        removeParticipant(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(0))
                .andExpect(jsonPath(participant(member) + ".status").value("REMOVED"))
                .andExpect(jsonPath(participant(member) + ".removedAt").isNotEmpty());

        assertThat(entriesOf(member)).isEmpty();
        assertInvariants();
    }

    @Test
    void remove_afterCharging_returnsTheFee() throws Exception {
        Member member = member(full);
        publish();
        add(member).andExpect(status().isOk());
        charge().andExpect(status().isOk());

        removeParticipant(member).andExpect(status().isOk());

        assertThat(balanceOf(member)).isZero();
        assertThat(entriesOf(member)).extracting(AccountEntry::getType)
                .containsExactly(AccountEntryTypeEnum.EVENT_FEE, AccountEntryTypeEnum.EVENT_FEE_REVERSAL);
        assertInvariants();
    }

    @Test
    void remove_alreadyRemovedOrNotParticipant_isRefused() throws Exception {
        Member member = member(full);
        add(member).andExpect(status().isOk());
        removeParticipant(member).andExpect(status().isOk());

        removeParticipant(member).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(152));
        removeParticipant(member(full)).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    void addAgain_afterRemoval_joinsWithNewTimeLevelAndFee_unchargedAgain() throws Exception {
        Member member = member(youth);
        publish();
        add(member).andExpect(status().isOk());
        charge().andExpect(status().isOk());
        removeParticipant(member).andExpect(status().isOk());
        entityManager.find(Member.class, member.getId()).setLevel(entityManager.getReference(MembershipLevel.class, full.getId()));
        forget();
        clock.advance(Duration.ofHours(1));

        add(member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped.length()").value(0))
                .andExpect(jsonPath(participant(member) + ".status").value("ACTIVE"))
                .andExpect(jsonPath(participant(member) + ".levelName").value(full.getName()))
                .andExpect(jsonPath(participant(member) + ".feeCents").value(2500))
                .andExpect(jsonPath(participant(member) + ".charged").value(false))
                .andExpect(jsonPath(participant(member) + ".removedAt").value(contains(nullValue())))
                .andExpect(jsonPath(participant(member) + ".addedAt").value("2026-12-01T00:30:00+02:00"));

        assertThat(balanceOf(member)).isZero();
        assertInvariants();
        charge().andExpect(status().isOk());
        assertThat(balanceOf(member)).isEqualTo(-2500);
        assertInvariants();
    }

    // ---- closing and cancelling ----

    @Test
    void close_chargesRemainingFeesAndLocksTheEvent() throws Exception {
        Member charged = member(full);
        publish();
        add(charged).andExpect(status().isOk());
        charge().andExpect(status().isOk());
        Member remaining = member(youth);
        add(remaining).andExpect(status().isOk());

        perform(post("/api/admin/events/" + eventId + "/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.signupOpen").value(false))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.unchargedCount").value(0));

        assertThat(balanceOf(charged)).isEqualTo(-1500);
        assertThat(balanceOf(remaining)).isEqualTo(-800);
        assertInvariants();
        add(member(full)).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(148));
        removeParticipant(charged).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(148));
        changeFee(charged, 0, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(148));
        charge().andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(148));
    }

    @Test
    void close_draftEvent_returns403() throws Exception {
        perform(post("/api/admin/events/" + eventId + "/close"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(150));
    }

    @Test
    void cancel_removesEveryoneAndReturnsOnlyChargedFees() throws Exception {
        Member charged = member(full);
        publish();
        add(charged).andExpect(status().isOk());
        charge().andExpect(status().isOk());
        Member uncharged = member(youth);
        add(uncharged).andExpect(status().isOk());

        perform(post("/api/admin/events/" + eventId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.signupOpen").value(false))
                .andExpect(jsonPath("$.participantCount").value(0))
                .andExpect(jsonPath("$.participants[*].status", containsInAnyOrder("REMOVED", "REMOVED")));

        assertThat(balanceOf(charged)).isZero();
        assertThat(entriesOf(charged)).extracting(AccountEntry::getType)
                .containsExactly(AccountEntryTypeEnum.EVENT_FEE, AccountEntryTypeEnum.EVENT_FEE_REVERSAL);
        assertThat(entriesOf(uncharged)).isEmpty();
        assertInvariants();
        perform(post("/api/admin/events/" + eventId + "/cancel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(148));
    }

    @Test
    void cancel_draftEvent_isAllowed() throws Exception {
        add(member(full)).andExpect(status().isOk());

        perform(post("/api/admin/events/" + eventId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertInvariants();
    }

    @Test
    void participants_listActiveByNameThenRemoved() throws Exception {
        Member removed = member(full, "Aadu");
        Member second = member(full, "Mari");
        Member first = member(full, "Kati");
        add(removed, second, first).andExpect(status().isOk());

        String json = removeParticipant(removed).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(json, "$.participants[*].displayName");
        assertThat(names).containsExactly(first.getDisplayName(), second.getDisplayName(), removed.getDisplayName());
    }

    // ---- invariants (DB-MODEL.md) ----

    // A participant's rows sum to -fee when active and charged, else 0; a cancelled event has nobody active, a
    // closed one has everyone active charged; every member's balance is the sum of their rows
    private void assertInvariants() {
        forget();
        List<EventParticipant> participants = entityManager.createQuery(
                        "select p from EventParticipant p join fetch p.event where p.event.id = :eventId", EventParticipant.class)
                .setParameter("eventId", eventId).getResultList();
        for (EventParticipant participant : participants) {
            long sum = entityManager.createQuery(
                            "select coalesce(sum(e.amountCents), 0) from AccountEntry e where e.eventParticipant.id = :id", Long.class)
                    .setParameter("id", participant.getId()).getSingleResult();
            long expected = participant.isActive() && participant.isCharged() ? -participant.getFeeCents() : 0;
            assertThat(sum).as("rows of participant %d", participant.getId()).isEqualTo(expected);
            Member member = entityManager.find(Member.class, participant.getMember().getId());
            assertThat(member.getBalanceCents()).isEqualTo(entriesOf(member).stream().mapToLong(AccountEntry::getAmountCents).sum());
            switch (participant.getEvent().getStatus()) {
                case CANCELLED -> assertThat(participant.isActive()).isFalse();
                case CLOSED -> assertThat(!participant.isActive() || participant.isCharged()).isTrue();
                default -> {
                }
            }
        }
    }

    // ---- helpers ----

    private MembershipLevel level(String name) {
        MembershipLevel level = new MembershipLevel();
        level.setName(name + " " + COUNTER.incrementAndGet());
        level.setSortOrder(900 + COUNTER.get());
        level.setActive(true);
        level.setCreatedAt(OffsetDateTime.now(clock));
        entityManager.persist(level);
        return level;
    }

    private Member member(MembershipLevel level) {
        return member(level, "Osaleja");
    }

    private Member member(MembershipLevel level, String name) {
        Member member = testSessions.createMember(MemberRoleEnum.MEMBER);
        member.setDisplayName(name + " " + COUNTER.incrementAndGet());
        member.setLevel(entityManager.getReference(MembershipLevel.class, level.getId()));
        forget();
        return member;
    }

    private Long createEvent(String name) throws Exception {
        String json = perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"startsAt\":\"2026-12-12T19:00:00+02:00\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.eventId")).longValue();
    }

    private String eventName() {
        return entityManager.find(Event.class, eventId).getName();
    }

    private void publish() throws Exception {
        perform(post("/api/admin/events/" + eventId + "/publish")).andExpect(status().isOk());
    }

    private ResultActions add(Member... members) throws Exception {
        return addIds(Arrays.stream(members).map(Member::getId).toArray(Long[]::new));
    }

    private ResultActions addIds(Long... memberIds) throws Exception {
        return addBody("{\"memberIds\":[" + Arrays.stream(memberIds).map(String::valueOf).collect(Collectors.joining(",")) + "]}");
    }

    private ResultActions addBody(String json) throws Exception {
        return perform(post("/api/admin/events/" + eventId + "/participants").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions charge() throws Exception {
        return perform(post("/api/admin/events/" + eventId + "/charge"));
    }

    private ResultActions changeFee(Member member, long feeCents, String note) throws Exception {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        return perform(put("/api/admin/events/" + eventId + "/participants/" + member.getId() + "/fee")
                .contentType(MediaType.APPLICATION_JSON).content("{\"feeCents\":" + feeCents + ",\"note\":" + noteJson + "}"));
    }

    private ResultActions removeParticipant(Member member) throws Exception {
        return perform(delete("/api/admin/events/" + eventId + "/participants/" + member.getId()));
    }

    private static String fee(MembershipLevel level, long amountCents) {
        return "{\"levelId\":" + level.getId() + ",\"amountCents\":" + amountCents + "}";
    }

    // In the detail view, whether at the top level or inside the add response
    private static String participant(Member member) {
        return "$..participants[?(@.memberId == " + member.getId() + ")]";
    }

    private EventParticipant participantRow(Member member) {
        return entityManager.createQuery(
                        "select p from EventParticipant p where p.event.id = :eventId and p.member.id = :memberId", EventParticipant.class)
                .setParameter("eventId", eventId).setParameter("memberId", member.getId())
                .getResultStream().findFirst().orElse(null);
    }

    private long balanceOf(Member member) {
        forget();
        return entityManager.find(Member.class, member.getId()).getBalanceCents();
    }

    private List<AccountEntry> entriesOf(Member member) {
        return entityManager.createQuery("select e from AccountEntry e where e.member.id = :memberId order by e.id", AccountEntry.class)
                .setParameter("memberId", member.getId()).getResultList();
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
