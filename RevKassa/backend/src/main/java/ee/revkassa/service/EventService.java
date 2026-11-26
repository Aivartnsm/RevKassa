package ee.revkassa.service;

import ee.revkassa.controller.admin.dashboard.dto.UpcomingEventDto;
import ee.revkassa.controller.admin.event.dto.EventCreateRequestDto;
import ee.revkassa.controller.admin.event.dto.EventDto;
import ee.revkassa.controller.admin.event.dto.EventFeeDto;
import ee.revkassa.controller.admin.event.dto.EventFeeRequestDto;
import ee.revkassa.controller.admin.event.dto.EventFeesUpdateRequestDto;
import ee.revkassa.controller.admin.event.dto.EventSummaryDto;
import ee.revkassa.controller.admin.event.dto.EventUpdateRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.event.EventMapper;
import ee.revkassa.persistence.event.EventRepository;
import ee.revkassa.persistence.event.EventStatusEnum;
import ee.revkassa.persistence.eventlevelfee.EventLevelFee;
import ee.revkassa.persistence.eventlevelfee.EventLevelFeeId;
import ee.revkassa.persistence.eventlevelfee.EventLevelFeeRepository;
import ee.revkassa.persistence.eventparticipant.EventParticipantRepository;
import ee.revkassa.persistence.eventparticipant.ParticipantTotal;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.persistence.membershiplevel.MembershipLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Club events and their fee per membership level (B28). Only a PLANNED event can be changed; closing and cancelling
 * come with participants (B29). Nothing is deleted: a mistaken event is cancelled. Saving the fee table raises the
 * event's version, so it conflicts with a parallel edit of the event like any other change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventLevelFeeRepository eventLevelFeeRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final MembershipLevelRepository membershipLevelRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<EventSummaryDto> getEvents() {
        List<Event> events = eventRepository.findAllEvents();
        Map<Long, ParticipantTotal> totals = participantTotalsOf(events.stream().map(Event::getId).toList());
        return events.stream()
                .map(event -> toEventSummaryDto(event, totals))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDto getEvent(Long eventId) {
        return toEventDto(getValidEventBy(eventId));
    }

    /**
     * A new event is PLANNED, with sign-up closed and no fees: the admin sets the fees before opening sign-up.
     */
    @Transactional
    public EventDto createEvent(EventCreateRequestDto request, Long adminId) {
        Event event = eventMapper.toEvent(request);
        normalizeTexts(event);
        event.setStatus(EventStatusEnum.PLANNED);
        event.setSignupOpen(false);
        event.setCreatedBy(memberRepository.getReferenceById(adminId));
        OffsetDateTime now = now();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        eventRepository.save(event);
        log.info("Event created: eventId={}, adminId={}", event.getId(), adminId);
        return toEventDto(event);
    }

    @Transactional
    public EventDto updateEvent(Long eventId, EventUpdateRequestDto request, Long adminId) {
        Event event = getValidEditableEventBy(eventId, request.getVersion());
        eventMapper.updateEvent(request, event);
        normalizeTexts(event);
        event.setUpdatedAt(now());
        eventRepository.flush();
        log.info("Event updated: eventId={}, signupOpen={}, adminId={}", eventId, event.getSignupOpen(), adminId);
        return toEventDto(event);
    }

    /**
     * Replaces the whole fee table: a level that is not listed pays 0. Only fees above 0 are stored.
     */
    @Transactional
    public EventDto updateFees(Long eventId, EventFeesUpdateRequestDto request, Long adminId) {
        Event event = getValidEditableEventBy(eventId, request.getVersion());
        Map<Long, MembershipLevel> levels = getValidLevelsBy(request.getFees());
        // The event row first: a parallel save waits for it and then fails on the version, before touching the fees
        event.setUpdatedAt(now());
        eventRepository.flush();
        eventLevelFeeRepository.deleteEventFees(eventId);
        eventLevelFeeRepository.saveAll(createFees(event, request.getFees(), levels));
        log.info("Event fees saved: eventId={}, levels={}, adminId={}", eventId, request.getFees().size(), adminId);
        return toEventDto(event);
    }

    /**
     * PLANNED events from now on, soonest first: the admin overview card.
     */
    @Transactional(readOnly = true)
    public List<UpcomingEventDto> getUpcomingEvents(int limit) {
        List<Event> events = eventRepository.findUpcoming(now(), PageRequest.of(0, limit));
        Map<Long, ParticipantTotal> totals = participantTotalsOf(events.stream().map(Event::getId).toList());
        return events.stream()
                .map(event -> toUpcomingEventDto(event, totals))
                .toList();
    }

    // ---- reading ----

    private Event getValidEventBy(Long eventId) {
        return eventRepository.findWithCreator(eventId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Events without active participants have no row in the query result
    private Map<Long, ParticipantTotal> participantTotalsOf(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return eventParticipantRepository.findActiveTotals(eventIds).stream()
                .collect(Collectors.toMap(ParticipantTotal::eventId, Function.identity()));
    }

    private EventSummaryDto toEventSummaryDto(Event event, Map<Long, ParticipantTotal> totals) {
        ParticipantTotal total = totals.getOrDefault(event.getId(), ParticipantTotal.none(event.getId()));
        EventSummaryDto dto = eventMapper.toEventSummaryDto(event);
        dto.setParticipantCount(total.participantCount());
        dto.setFeeIncomeCents(total.feeCents());
        return dto;
    }

    private EventDto toEventDto(Event event) {
        ParticipantTotal total = participantTotalsOf(List.of(event.getId()))
                .getOrDefault(event.getId(), ParticipantTotal.none(event.getId()));
        EventDto dto = eventMapper.toEventDto(event);
        dto.setParticipantCount(total.participantCount());
        dto.setFeeIncomeCents(total.feeCents());
        dto.setFees(feesOf(event));
        return dto;
    }

    // Every active level; a deactivated level only when it has a fee, as its members can still take part
    private List<EventFeeDto> feesOf(Event event) {
        Map<Long, Long> amounts = eventLevelFeeRepository.findEventFees(event.getId()).stream()
                .collect(Collectors.toMap(fee -> fee.getId().getLevelId(), EventLevelFee::getAmountCents));
        return membershipLevelRepository.findAllLevels().stream()
                .filter(level -> level.getActive() || amounts.containsKey(level.getId()))
                .map(level -> new EventFeeDto(level.getId(), level.getName(), level.getActive(),
                        amounts.getOrDefault(level.getId(), 0L)))
                .toList();
    }

    private UpcomingEventDto toUpcomingEventDto(Event event, Map<Long, ParticipantTotal> totals) {
        UpcomingEventDto dto = eventMapper.toUpcomingEventDto(event);
        dto.setParticipantCount(totals.getOrDefault(event.getId(), ParticipantTotal.none(event.getId())).participantCount());
        return dto;
    }

    // ---- changing ----

    private Event getValidEditableEventBy(Long eventId, Long requestVersion) {
        Event event = getValidEventBy(eventId);
        if (!event.isEditable()) {
            throw new ForbiddenException(Error.EVENT_NOT_EDITABLE);
        }
        validateVersion(event, requestVersion);
        return event;
    }

    // JPA checks the version it loaded itself; this compares the version the admin's form was loaded with
    private static void validateVersion(Event event, Long requestVersion) {
        if (!event.getVersion().equals(requestVersion)) {
            throw new ObjectOptimisticLockingFailureException(Event.class, event.getId());
        }
    }

    private Map<Long, MembershipLevel> getValidLevelsBy(List<EventFeeRequestDto> fees) {
        List<Long> levelIds = fees.stream().map(EventFeeRequestDto::getLevelId).toList();
        Map<Long, MembershipLevel> levels = membershipLevelRepository.findAllById(levelIds).stream()
                .collect(Collectors.toMap(MembershipLevel::getId, Function.identity()));
        if (levels.size() != levelIds.size()) {
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
        return levels;
    }

    private static List<EventLevelFee> createFees(Event event, List<EventFeeRequestDto> fees,
                                                  Map<Long, MembershipLevel> levels) {
        return fees.stream()
                .filter(fee -> fee.getAmountCents() > 0)
                .map(fee -> createFee(event, levels.get(fee.getLevelId()), fee.getAmountCents()))
                .toList();
    }

    private static EventLevelFee createFee(Event event, MembershipLevel level, long amountCents) {
        EventLevelFeeId id = new EventLevelFeeId();
        id.setEventId(event.getId());
        id.setLevelId(level.getId());
        EventLevelFee fee = new EventLevelFee();
        fee.setId(id);
        fee.setEvent(event);
        fee.setLevel(level);
        fee.setAmountCents(amountCents);
        return fee;
    }

    private static void normalizeTexts(Event event) {
        event.setName(event.getName().strip());
        event.setLocation(blankToNull(event.getLocation()));
        event.setDescription(blankToNull(event.getDescription()));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
