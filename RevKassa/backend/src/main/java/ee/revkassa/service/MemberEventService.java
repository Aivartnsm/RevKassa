package ee.revkassa.service;

import ee.revkassa.controller.event.dto.MemberEventDto;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.security.LoginMethodEnum;
import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.event.EventMapper;
import ee.revkassa.persistence.event.EventRepository;
import ee.revkassa.persistence.event.EventStatusEnum;
import ee.revkassa.persistence.eventlevelfee.EventLevelFee;
import ee.revkassa.persistence.eventlevelfee.EventLevelFeeRepository;
import ee.revkassa.persistence.eventparticipant.EventParticipant;
import ee.revkassa.persistence.eventparticipant.EventParticipantRepository;
import ee.revkassa.persistence.eventparticipant.ParticipantTotal;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Events as a member sees them, and signing up (B33). A member sees published events from today on (Tallinn date),
 * earlier published events they take part in, and their latest closed ones; never drafts or cancelled events.
 * Signing up works like an admin adding them (EventParticipantService.join) with the member as addedBy, needs a
 * password session (not the shared bar tablet's PIN) and only follows the admin's sign-up switch. The balance does
 * not change until the admin charges the fees. Members cannot remove themselves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberEventService {

    public static final int CLOSED_EVENTS_SHOWN = 10;

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventLevelFeeRepository eventLevelFeeRepository;
    private final MemberRepository memberRepository;
    private final EventParticipantService eventParticipantService;
    private final Clock clock;

    /**
     * Published events soonest first, then closed ones newest first.
     */
    @Transactional(readOnly = true)
    public List<MemberEventDto> getEvents(Long memberId) {
        Member member = getValidMemberBy(memberId);
        OffsetDateTime startOfToday = today().atStartOfDay(ClockConfig.APP_ZONE).toOffsetDateTime();
        List<Event> events = Stream.concat(
                        eventRepository.findPublishedForMember(memberId, startOfToday).stream(),
                        eventRepository.findClosedForMember(memberId, PageRequest.of(0, CLOSED_EVENTS_SHOWN)).stream())
                .toList();
        return toMemberEventDtos(events, member);
    }

    @Transactional
    public MemberEventDto signUp(Long eventId, Long memberId, LoginMethodEnum loginMethod) {
        validatePasswordLogin(loginMethod);
        Event event = getValidSignupEventBy(eventId);
        EventParticipant previous = eventParticipantRepository.findByMember(eventId, memberId).orElse(null);
        if (previous != null && previous.isActive()) {
            throw new ForbiddenException(Error.ALREADY_SIGNED_UP);
        }
        Member member = getValidMemberBy(memberId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        long feeCents = EventFeeTable.of(eventLevelFeeRepository.findEventFees(eventId))
                .feeOn(EventFeeTable.dayOf(now), member.getLevel().getId());
        eventParticipantService.join(event, member, previous, feeCents, member, now);
        log.info("Member signed up: eventId={}, memberId={}, again={}", eventId, memberId, previous != null);
        return toMemberEventDtos(List.of(event), member).getFirst();
    }

    // ---- reading ----

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findWithLevels(List.of(memberId)).stream()
                .findFirst()
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Everything the cards need in three queries, however many events
    private List<MemberEventDto> toMemberEventDtos(List<Event> events, Member member) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, EventParticipant> participations = eventParticipantRepository
                .findMemberParticipations(member.getId(), eventIds).stream()
                .collect(Collectors.toMap(participant -> participant.getEvent().getId(), Function.identity()));
        Map<Long, Long> participantCounts = eventParticipantRepository.findActiveTotals(eventIds).stream()
                .collect(Collectors.toMap(ParticipantTotal::eventId, ParticipantTotal::participantCount));
        Map<Long, List<EventLevelFee>> fees = eventLevelFeeRepository.findFeesOfEvents(eventIds).stream()
                .collect(Collectors.groupingBy(fee -> fee.getEvent().getId()));
        LocalDate today = today();
        return events.stream()
                .map(event -> toMemberEventDto(event, member, participations.get(event.getId()),
                        participantCounts.getOrDefault(event.getId(), 0L),
                        EventFeeTable.of(fees.getOrDefault(event.getId(), List.of())), today))
                .toList();
    }

    private MemberEventDto toMemberEventDto(Event event, Member member, EventParticipant participation,
                                            long participantCount, EventFeeTable fees, LocalDate today) {
        MemberEventDto dto = eventMapper.toMemberEventDto(event);
        dto.setParticipantCount(participantCount);
        if (participation != null && participation.isActive()) {
            dto.setSignedUp(true);
            dto.setCharged(participation.isCharged());
            dto.setFeeCents(participation.getFeeCents());
            return dto;
        }
        Long levelId = member.getLevel().getId();
        dto.setFeeCents(fees.feeOn(today, levelId));
        dto.setNextFee(fees.nextChangeAfter(today, levelId).orElse(null));
        return dto;
    }

    private LocalDate today() {
        return EventFeeTable.dayOf(OffsetDateTime.now(clock));
    }

    // ---- signing up ----

    // A shared tablet's PIN session must not sign someone else up: it needs the member's own login
    private static void validatePasswordLogin(LoginMethodEnum loginMethod) {
        if (loginMethod != LoginMethodEnum.PASSWORD) {
            throw new ForbiddenException(Error.SIGNUP_NEEDS_PASSWORD);
        }
    }

    // Locks the event row first, as every participant change does. A draft is as unknown to a member as a missing id
    private Event getValidSignupEventBy(Long eventId) {
        Event event = eventRepository.findForUpdate(eventId)
                .filter(found -> found.getStatus() != EventStatusEnum.DRAFT)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
        if (event.getStatus() != EventStatusEnum.PUBLISHED || !event.getSignupOpen()) {
            throw new ForbiddenException(Error.SIGNUP_CLOSED);
        }
        return event;
    }
}
