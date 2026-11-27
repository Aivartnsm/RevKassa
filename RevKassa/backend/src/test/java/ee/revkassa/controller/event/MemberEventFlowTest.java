package ee.revkassa.controller.event;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.event.EventStatusEnum;
import ee.revkassa.persistence.eventlevelfee.EventLevelFee;
import ee.revkassa.persistence.eventparticipant.EventParticipant;
import ee.revkassa.persistence.eventparticipant.EventParticipantStatusEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B33: the member's events and signing up, through the real security chain and PostgreSQL. The clock starts at
 * 23:30 Tallinn time on 30.11; the fee table has a price period from 01.12. The dev data has events of its own, so
 * assertions pick out this test's events by id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class MemberEventFlowTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    // 23:30 on 30.11 in Tallinn
    private static final Instant START = Instant.parse("2026-11-30T21:30:00Z");
    private static final LocalDate FROM_DECEMBER = LocalDate.parse("2026-12-01");

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

    private Member admin;
    private MembershipLevel full;
    private MembershipLevel youth;
    private Member member;
    private MockHttpSession memberSession;

    @BeforeEach
    void createMemberAndLevels() throws Exception {
        clock.set(START);
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        full = level("Täis");
        youth = level("Noor");
        member = member(full);
        memberSession = testSessions.passwordSession(member);
        forget();
    }

    // ---- listing ----

    @Test
    void list_showsPublishedFromTodayAndOwnEvents_neverDraftsCancelledOrOthersPast() throws Exception {
        Event tomorrow = event(now().plusDays(1), EventStatusEnum.PUBLISHED, true);
        Event earlierToday = event(now().minusHours(12), EventStatusEnum.PUBLISHED, false);
        Event pastJoined = event(now().minusDays(10), EventStatusEnum.PUBLISHED, false);
        participant(pastJoined, member, EventParticipantStatusEnum.ACTIVE, false);
        Event pastNotJoined = event(now().minusDays(10), EventStatusEnum.PUBLISHED, false);
        Event draft = event(now().plusDays(2), EventStatusEnum.DRAFT, false);
        Event cancelled = event(now().plusDays(3), EventStatusEnum.CANCELLED, false);
        Event closedJoined = event(now().minusDays(20), EventStatusEnum.CLOSED, false);
        participant(closedJoined, member, EventParticipantStatusEnum.ACTIVE, true);
        Event closedRemoved = event(now().minusDays(15), EventStatusEnum.CLOSED, false);
        participant(closedRemoved, member, EventParticipantStatusEnum.REMOVED, true);
        Event closedNotJoined = event(now().minusDays(5), EventStatusEnum.CLOSED, false);

        List<Long> ours = ourIds(list(), List.of(tomorrow, earlierToday, pastJoined, pastNotJoined, draft, cancelled,
                closedJoined, closedRemoved, closedNotJoined));

        assertThat(ours).containsExactly(pastJoined.getId(), earlierToday.getId(), tomorrow.getId(), closedJoined.getId());
    }

    @Test
    void list_closedEvents_atMostTenNewestFirst() throws Exception {
        List<Event> closed = new ArrayList<>();
        for (int day = 30; day > 19; day--) {
            Event event = event(now().minusDays(day), EventStatusEnum.CLOSED, false);
            participant(event, member, EventParticipantStatusEnum.ACTIVE, true);
            closed.add(event);
        }

        List<Long> ours = ourIds(list(), closed);

        assertThat(ours).hasSize(10).first().isEqualTo(closed.getLast().getId());
        assertThat(ours).doesNotContain(closed.getFirst().getId());
    }

    @Test
    void list_notSignedUp_showsTodaysFeeOfOwnLevelAndNextChange() throws Exception {
        Event event = event(now().plusDays(10), EventStatusEnum.PUBLISHED, true);
        Member young = member(youth);

        list().andExpect(jsonPath(card(event) + ".feeCents").value(1500))
                .andExpect(jsonPath(card(event) + ".signedUp").value(false))
                .andExpect(jsonPath(card(event) + ".nextFee.validFrom").value("2026-12-01"))
                .andExpect(jsonPath(card(event) + ".nextFee.feeCents").value(2500))
                .andExpect(jsonPath(card(event) + ".signupOpen").value(true))
                .andExpect(jsonPath(card(event) + ".status").value("PUBLISHED"));
        // Youth has no row from 01.12: that period is free for them
        mockMvc.perform(get("/api/events").session(testSessions.passwordSession(young)))
                .andExpect(jsonPath(card(event) + ".feeCents").value(800))
                .andExpect(jsonPath(card(event) + ".nextFee.feeCents").value(0));
        clock.advance(Duration.ofHours(1)); // 00:30 on 01.12 in Tallinn
        list().andExpect(jsonPath(card(event) + ".feeCents").value(2500))
                .andExpect(jsonPath(card(event) + ".nextFee").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void list_signedUp_showsCopiedFeeAndWhetherCharged() throws Exception {
        Event event = event(now().plusDays(10), EventStatusEnum.PUBLISHED, true);
        EventParticipant participation = participant(event, member, EventParticipantStatusEnum.ACTIVE, false);
        participation.setFeeCents(1234L);
        forget();
        participant(event, member(full), EventParticipantStatusEnum.ACTIVE, false);

        list().andExpect(jsonPath(card(event) + ".signedUp").value(true))
                .andExpect(jsonPath(card(event) + ".feeCents").value(1234))
                .andExpect(jsonPath(card(event) + ".charged").value(false))
                .andExpect(jsonPath(card(event) + ".participantCount").value(2))
                .andExpect(jsonPath(card(event) + ".nextFee").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    // ---- signing up ----

    @Test
    void signup_copiesLevelAndTodaysFee_addedBySelf_balanceUnchanged() throws Exception {
        Event event = event(now().plusDays(10), EventStatusEnum.PUBLISHED, true);

        signUp(event, memberSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId()))
                .andExpect(jsonPath("$.signedUp").value(true))
                .andExpect(jsonPath("$.charged").value(false))
                .andExpect(jsonPath("$.feeCents").value(1500))
                .andExpect(jsonPath("$.participantCount").value(1));

        EventParticipant row = participation(event, member);
        assertThat(row.getStatus()).isEqualTo(EventParticipantStatusEnum.ACTIVE);
        assertThat(row.getLevel().getId()).isEqualTo(full.getId());
        assertThat(row.isSelfSignup()).isTrue();
        assertThat(row.getChargedAt()).isNull();
        assertThat(entityManager.find(Member.class, member.getId()).getBalanceCents()).isZero();
        assertThat(entryCount(member)).isZero();
    }

    @Test
    void signup_feeFollowsTheMembersLevelAndTheTallinnDay() throws Exception {
        Event event = event(now().plusDays(10), EventStatusEnum.PUBLISHED, true);
        Member young = member(youth);
        signUp(event, testSessions.passwordSession(young)).andExpect(jsonPath("$.feeCents").value(800));
        clock.advance(Duration.ofHours(1)); // 01.12 in Tallinn, still 30.11 in UTC

        signUp(event, memberSession).andExpect(jsonPath("$.feeCents").value(2500));
    }

    @Test
    void signup_stillOpenAfterTheStart_followsOnlyTheSwitch() throws Exception {
        Event started = event(now().minusHours(2), EventStatusEnum.PUBLISHED, true);

        signUp(started, memberSession).andExpect(status().isOk());
    }

    @Test
    void signup_closedSwitchOrClosedOrCancelledEvent_returns403SignupClosed() throws Exception {
        for (Event event : List.of(event(now().plusDays(1), EventStatusEnum.PUBLISHED, false),
                event(now().minusDays(1), EventStatusEnum.CLOSED, false),
                event(now().plusDays(1), EventStatusEnum.CANCELLED, false))) {
            signUp(event, memberSession)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(153));
            assertThat(participation(event, member)).isNull();
        }
    }

    @Test
    void signup_draftOrUnknownEvent_returns404() throws Exception {
        Event draft = event(now().plusDays(1), EventStatusEnum.DRAFT, false);

        signUp(draft, memberSession).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value(101));
        mockMvc.perform(post("/api/events/" + Long.MAX_VALUE + "/signup").with(csrf()).session(memberSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void signup_twice_returns403AlreadySignedUp() throws Exception {
        Event event = event(now().plusDays(1), EventStatusEnum.PUBLISHED, true);
        signUp(event, memberSession).andExpect(status().isOk());

        signUp(event, memberSession).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(154));
    }

    @Test
    void signup_withPinOnTheBarTablet_returns403_butListWorks() throws Exception {
        Event event = event(now().plusDays(1), EventStatusEnum.PUBLISHED, true);
        MockHttpSession pinSession = testSessions.pinSession(MemberRoleEnum.MEMBER);

        signUp(event, pinSession).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value(155));
        mockMvc.perform(get("/api/events").session(pinSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath(card(event) + ".signedUp").value(false));
    }

    @Test
    void signup_withoutCsrfToken_returns403() throws Exception {
        Event event = event(now().plusDays(1), EventStatusEnum.PUBLISHED, true);

        mockMvc.perform(post("/api/events/" + event.getId() + "/signup").session(memberSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void signup_withoutSession_returns401() throws Exception {
        mockMvc.perform(post("/api/events/1/signup").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void signup_afterAdminRemoval_joinsAgainLikeNew() throws Exception {
        Event event = event(now().plusDays(10), EventStatusEnum.PUBLISHED, true);
        participant(event, member, EventParticipantStatusEnum.REMOVED, true);
        clock.advance(Duration.ofHours(1));

        signUp(event, memberSession).andExpect(status().isOk()).andExpect(jsonPath("$.feeCents").value(2500));

        EventParticipant row = participation(event, member);
        assertThat(row.getStatus()).isEqualTo(EventParticipantStatusEnum.ACTIVE);
        assertThat(row.getChargedAt()).isNull();
        assertThat(row.getRemovedAt()).isNull();
        assertThat(row.isSelfSignup()).isTrue();
    }

    @Test
    void member_cannotRemoveThemselves_butAdminCan() throws Exception {
        Event event = event(now().plusDays(10), EventStatusEnum.PUBLISHED, true);
        signUp(event, memberSession).andExpect(status().isOk());

        // No member route for it, and the admin's route is closed to members
        mockMvc.perform(delete("/api/events/" + event.getId() + "/signup").with(csrf()).session(memberSession))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/admin/events/" + event.getId() + "/participants/" + member.getId())
                        .with(csrf()).session(memberSession))
                .andExpect(status().isForbidden());
        forget();
        assertThat(participation(event, member).getStatus()).isEqualTo(EventParticipantStatusEnum.ACTIVE);

        mockMvc.perform(delete("/api/admin/events/" + event.getId() + "/participants/" + member.getId())
                        .with(csrf()).session(testSessions.passwordSession(admin)))
                .andExpect(status().isOk());
        forget();
        assertThat(participation(event, member).getStatus()).isEqualTo(EventParticipantStatusEnum.REMOVED);
    }

    // ---- helpers ----

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private MembershipLevel level(String name) {
        MembershipLevel level = new MembershipLevel();
        level.setName(name + " " + COUNTER.incrementAndGet());
        level.setSortOrder(900 + COUNTER.get());
        level.setActive(true);
        level.setCreatedAt(now());
        entityManager.persist(level);
        return level;
    }

    private Member member(MembershipLevel level) {
        Member created = testSessions.createMember(MemberRoleEnum.MEMBER);
        created.setLevel(entityManager.getReference(MembershipLevel.class, level.getId()));
        forget();
        return created;
    }

    // From the start: full 15 €, youth 8 €; from 01.12: full 25 €, youth has no row (= 0)
    private Event event(OffsetDateTime startsAt, EventStatusEnum status, boolean signupOpen) {
        Event event = new Event();
        event.setName("Üritus " + COUNTER.incrementAndGet());
        event.setStartsAt(startsAt);
        event.setStatus(status);
        event.setSignupOpen(signupOpen);
        event.setCreatedBy(entityManager.getReference(Member.class, admin.getId()));
        event.setCreatedAt(now());
        event.setUpdatedAt(now());
        entityManager.persist(event);
        fee(event, full, null, 1500);
        fee(event, youth, null, 800);
        fee(event, full, FROM_DECEMBER, 2500);
        forget();
        return event;
    }

    private void fee(Event event, MembershipLevel level, LocalDate validFrom, long amountCents) {
        EventLevelFee fee = new EventLevelFee();
        fee.setEvent(event);
        fee.setLevel(entityManager.getReference(MembershipLevel.class, level.getId()));
        fee.setValidFrom(validFrom);
        fee.setAmountCents(amountCents);
        entityManager.persist(fee);
    }

    private EventParticipant participant(Event event, Member who, EventParticipantStatusEnum status, boolean charged) {
        EventParticipant participant = new EventParticipant();
        participant.setEvent(entityManager.getReference(Event.class, event.getId()));
        participant.setMember(entityManager.getReference(Member.class, who.getId()));
        participant.setLevel(entityManager.getReference(MembershipLevel.class, full.getId()));
        participant.setFeeCents(1500L);
        participant.setStatus(status);
        participant.setAddedBy(entityManager.getReference(Member.class, admin.getId()));
        participant.setAddedAt(now().minusDays(1));
        participant.setChargedAt(charged ? now().minusDays(1) : null);
        participant.setRemovedAt(status == EventParticipantStatusEnum.REMOVED ? now().minusHours(1) : null);
        entityManager.persist(participant);
        entityManager.flush();
        return participant;
    }

    private EventParticipant participation(Event event, Member who) {
        return entityManager.createQuery("select p from EventParticipant p join fetch p.member join fetch p.addedBy "
                        + "where p.event.id = :eventId and p.member.id = :memberId", EventParticipant.class)
                .setParameter("eventId", event.getId()).setParameter("memberId", who.getId())
                .getResultStream().findFirst().orElse(null);
    }

    private long entryCount(Member who) {
        return entityManager.createQuery("select count(e) from AccountEntry e where e.member.id = :memberId", Long.class)
                .setParameter("memberId", who.getId()).getSingleResult();
    }

    private ResultActions list() throws Exception {
        return mockMvc.perform(get("/api/events").session(memberSession)).andExpect(status().isOk());
    }

    private ResultActions signUp(Event event, MockHttpSession session) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/events/" + event.getId() + "/signup").with(csrf()).session(session));
        forget();
        return result;
    }

    private static String card(Event event) {
        return "$[?(@.eventId == " + event.getId() + ")]";
    }

    // The listed ids of this test's events, in the listed order
    private static List<Long> ourIds(ResultActions list, List<Event> ours) throws Exception {
        List<Number> ids = JsonPath.read(list.andReturn().getResponse().getContentAsString(), "$[*].eventId");
        List<Long> ourIds = ours.stream().map(Event::getId).toList();
        return ids.stream().map(Number::longValue).filter(ourIds::contains).toList();
    }

    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
