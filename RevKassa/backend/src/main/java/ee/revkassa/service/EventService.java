package ee.revkassa.service;

import ee.revkassa.controller.admin.dashboard.dto.UpcomingEventDto;
import ee.revkassa.controller.admin.event.dto.EventCreateRequestDto;
import ee.revkassa.controller.admin.event.dto.EventDto;
import ee.revkassa.controller.admin.event.dto.EventFeeDto;
import ee.revkassa.controller.admin.event.dto.EventFeePeriodDto;
import ee.revkassa.controller.admin.event.dto.EventFeePeriodRequestDto;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Club events and their fee table (B28). A new event is a DRAFT that members do not see; publishing shows it to them
 * and only then can sign-up be opened. Only DRAFT and PUBLISHED events can be changed. Nothing is deleted: a mistaken
 * event is cancelled. The fee table has price periods (from a date on) with a fee per level; saving it raises the
 * event's version, so it conflicts with a parallel edit of the event like any other change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private static final Comparator<LocalDate> FROM_THE_START_FIRST = Comparator.nullsFirst(Comparator.naturalOrder());

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
     * A new event is a DRAFT with sign-up closed and no fees: the admin sets the fees, publishes, then opens sign-up.
     */
    @Transactional
    public EventDto createEvent(EventCreateRequestDto request, Long adminId) {
        Event event = eventMapper.toEvent(request);
        normalizeTexts(event);
        event.setStatus(EventStatusEnum.DRAFT);
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
        validateSignupAllowed(event, request.getSignupOpen());
        eventMapper.updateEvent(request, event);
        normalizeTexts(event);
        event.setUpdatedAt(now());
        eventRepository.flush();
        log.info("Event updated: eventId={}, signupOpen={}, adminId={}", eventId, event.getSignupOpen(), adminId);
        return toEventDto(event);
    }

    @Transactional
    public EventDto publishEvent(Long eventId, Long adminId) {
        Event event = getValidEventForUpdateBy(eventId);
        validateEditable(event);
        if (event.getStatus() != EventStatusEnum.DRAFT) {
            throw new ForbiddenException(Error.EVENT_ALREADY_PUBLISHED);
        }
        changeStatus(event, EventStatusEnum.PUBLISHED);
        log.info("Event published: eventId={}, adminId={}", eventId, adminId);
        return toEventDto(event);
    }

    /**
     * Only while nobody takes part: members who joined must not lose sight of the event. Sign-up closes.
     */
    @Transactional
    public EventDto unpublishEvent(Long eventId, Long adminId) {
        Event event = getValidEventForUpdateBy(eventId);
        validatePublished(event);
        if (participantTotalsOf(List.of(eventId)).containsKey(eventId)) {
            throw new ForbiddenException(Error.EVENT_HAS_PARTICIPANTS);
        }
        event.setSignupOpen(false);
        changeStatus(event, EventStatusEnum.DRAFT);
        log.info("Event unpublished: eventId={}, adminId={}", eventId, adminId);
        return toEventDto(event);
    }

    /**
     * Replaces the whole fee table. Rows of 0 are stored too, so a period with only free levels stays a period.
     */
    @Transactional
    public EventDto updateFees(Long eventId, EventFeesUpdateRequestDto request, Long adminId) {
        Event event = getValidEditableEventBy(eventId, request.getVersion());
        Map<Long, MembershipLevel> levels = getValidLevelsBy(request.getPeriods());
        // The event row first: a parallel save waits for it and then fails on the version, before touching the fees
        event.setUpdatedAt(now());
        eventRepository.flush();
        eventLevelFeeRepository.deleteEventFees(eventId);
        eventLevelFeeRepository.saveAll(createFees(event, request.getPeriods(), levels));
        log.info("Event fees saved: eventId={}, periods={}, adminId={}", eventId, request.getPeriods().size(), adminId);
        return toEventDto(event);
    }

    /**
     * Draft and published events from now on, soonest first: the admin overview card.
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
        dto.setFeePeriods(feePeriodsOf(event));
        return dto;
    }

    /**
     * Every period, the one from the start always included, each with the same levels: every active level and the
     * deactivated levels that have a fee above 0 somewhere, as their members can still take part.
     */
    private List<EventFeePeriodDto> feePeriodsOf(Event event) {
        List<EventLevelFee> rows = eventLevelFeeRepository.findEventFees(event.getId());
        Map<FeeKey, Long> amounts = rows.stream()
                .collect(Collectors.toMap(row -> new FeeKey(row.getValidFrom(), row.getLevel().getId()),
                        EventLevelFee::getAmountCents));
        Set<Long> levelsWithFee = rows.stream()
                .filter(row -> row.getAmountCents() > 0)
                .map(row -> row.getLevel().getId())
                .collect(Collectors.toSet());
        List<MembershipLevel> levels = membershipLevelRepository.findAllLevels().stream()
                .filter(level -> level.getActive() || levelsWithFee.contains(level.getId()))
                .toList();
        return periodStartsOf(rows).stream()
                .map(validFrom -> new EventFeePeriodDto(validFrom, feesOf(validFrom, levels, amounts)))
                .toList();
    }

    private static List<LocalDate> periodStartsOf(List<EventLevelFee> rows) {
        Set<LocalDate> starts = new TreeSet<>(FROM_THE_START_FIRST);
        starts.add(null);
        rows.forEach(row -> starts.add(row.getValidFrom()));
        return new ArrayList<>(starts);
    }

    private static List<EventFeeDto> feesOf(LocalDate validFrom, List<MembershipLevel> levels, Map<FeeKey, Long> amounts) {
        return levels.stream()
                .map(level -> new EventFeeDto(level.getId(), level.getName(), level.getActive(),
                        amounts.getOrDefault(new FeeKey(validFrom, level.getId()), 0L)))
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
        validateEditable(event);
        validateVersion(event, requestVersion);
        return event;
    }

    private Event getValidEventForUpdateBy(Long eventId) {
        return eventRepository.findForUpdate(eventId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private static void validateEditable(Event event) {
        if (!event.isEditable()) {
            throw new ForbiddenException(Error.EVENT_NOT_EDITABLE);
        }
    }

    private static void validatePublished(Event event) {
        validateEditable(event);
        if (event.getStatus() != EventStatusEnum.PUBLISHED) {
            throw new ForbiddenException(Error.EVENT_NOT_PUBLISHED);
        }
    }

    // JPA checks the version it loaded itself; this compares the version the admin's form was loaded with
    private static void validateVersion(Event event, Long requestVersion) {
        if (!event.getVersion().equals(requestVersion)) {
            throw new ObjectOptimisticLockingFailureException(Event.class, event.getId());
        }
    }

    // Members can only sign up to an event they can see
    private static void validateSignupAllowed(Event event, boolean signupOpen) {
        if (signupOpen && event.getStatus() != EventStatusEnum.PUBLISHED) {
            throw new ForbiddenException(Error.EVENT_NOT_PUBLISHED);
        }
    }

    private void changeStatus(Event event, EventStatusEnum status) {
        event.setStatus(status);
        event.setUpdatedAt(now());
        eventRepository.flush();
    }

    private Map<Long, MembershipLevel> getValidLevelsBy(List<EventFeePeriodRequestDto> periods) {
        List<Long> levelIds = periods.stream()
                .flatMap(period -> period.getFees().stream())
                .map(EventFeeRequestDto::getLevelId)
                .distinct()
                .toList();
        Map<Long, MembershipLevel> levels = membershipLevelRepository.findAllById(levelIds).stream()
                .collect(Collectors.toMap(MembershipLevel::getId, Function.identity()));
        if (levels.size() != levelIds.size()) {
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
        return levels;
    }

    private static List<EventLevelFee> createFees(Event event, List<EventFeePeriodRequestDto> periods,
                                                  Map<Long, MembershipLevel> levels) {
        return periods.stream()
                .flatMap(period -> period.getFees().stream()
                        .map(fee -> createFee(event, period.getValidFrom(), levels.get(fee.getLevelId()), fee.getAmountCents())))
                .toList();
    }

    private static EventLevelFee createFee(Event event, LocalDate validFrom, MembershipLevel level, long amountCents) {
        EventLevelFee fee = new EventLevelFee();
        fee.setEvent(event);
        fee.setLevel(level);
        fee.setValidFrom(validFrom);
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

    // validFrom null = the period from the start
    private record FeeKey(LocalDate validFrom, Long levelId) {
    }
}
