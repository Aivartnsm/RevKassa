package ee.revkassa.service;

import ee.revkassa.controller.admin.event.dto.BudgetCategoryDto;
import ee.revkassa.controller.admin.event.dto.BudgetLineCreateRequestDto;
import ee.revkassa.controller.admin.event.dto.BudgetLineDto;
import ee.revkassa.controller.admin.event.dto.BudgetLineUpdateRequestDto;
import ee.revkassa.controller.admin.event.dto.EventBudgetDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.event.EventRepository;
import ee.revkassa.persistence.event.EventStatusEnum;
import ee.revkassa.persistence.eventbudgetline.BudgetCategoryEnum;
import ee.revkassa.persistence.eventbudgetline.BudgetLineSourceEnum;
import ee.revkassa.persistence.eventbudgetline.EventBudgetLine;
import ee.revkassa.persistence.eventbudgetline.EventBudgetLineMapper;
import ee.revkassa.persistence.eventbudgetline.EventBudgetLineRepository;
import ee.revkassa.persistence.eventparticipant.EventParticipantRepository;
import ee.revkassa.persistence.eventparticipant.ParticipantTotal;
import ee.revkassa.persistence.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An event's budget (B30): planned and actual costs per category against the participants' fees. A planning sheet
 * only: it never touches balances or stock. Lines can be changed until the event is cancelled; a closed event's
 * lines stay open, as actual amounts often come from receipts after closing. Every change locks the event row
 * first, so a change cannot slip past a parallel cancel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventBudgetService {

    private final EventRepository eventRepository;
    private final EventBudgetLineRepository eventBudgetLineRepository;
    private final EventBudgetLineMapper eventBudgetLineMapper;
    private final EventParticipantRepository eventParticipantRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EventBudgetDto getBudget(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
        return budgetOf(eventId, feeIncomeOf(eventId));
    }

    /**
     * A new line goes last in its category.
     */
    @Transactional
    public EventBudgetDto createLine(Long eventId, BudgetLineCreateRequestDto request, Long adminId) {
        Event event = getValidChangeableEventBy(eventId);
        EventBudgetLine line = eventBudgetLineMapper.toEventBudgetLine(request);
        normalizeTexts(line);
        line.setEvent(event);
        line.setSource(Objects.requireNonNullElse(request.getSource(), BudgetLineSourceEnum.MANUAL));
        line.setSortOrder(nextSortOrder(eventId, line.getCategory()));
        line.setCreatedBy(memberRepository.getReferenceById(adminId));
        OffsetDateTime now = now();
        line.setCreatedAt(now);
        line.setUpdatedAt(now);
        eventBudgetLineRepository.save(line);
        log.info("Budget line created: eventId={}, lineId={}, source={}, adminId={}", eventId, line.getId(), line.getSource(), adminId);
        return budgetOf(eventId, feeIncomeOf(eventId));
    }

    /**
     * A line moved to another category goes last there. The source stays as it was.
     */
    @Transactional
    public EventBudgetDto updateLine(Long eventId, Long lineId, BudgetLineUpdateRequestDto request, Long adminId) {
        getValidChangeableEventBy(eventId);
        EventBudgetLine line = getValidLineBy(eventId, lineId);
        if (line.getCategory() != request.getCategory()) {
            line.setSortOrder(nextSortOrder(eventId, request.getCategory()));
        }
        eventBudgetLineMapper.updateEventBudgetLine(request, line);
        normalizeTexts(line);
        line.setUpdatedAt(now());
        log.info("Budget line updated: eventId={}, lineId={}, adminId={}", eventId, lineId, adminId);
        return budgetOf(eventId, feeIncomeOf(eventId));
    }

    @Transactional
    public EventBudgetDto deleteLine(Long eventId, Long lineId, Long adminId) {
        getValidChangeableEventBy(eventId);
        eventBudgetLineRepository.delete(getValidLineBy(eventId, lineId));
        log.info("Budget line deleted: eventId={}, lineId={}, adminId={}", eventId, lineId, adminId);
        return budgetOf(eventId, feeIncomeOf(eventId));
    }

    /**
     * Every category in BudgetCategoryEnum order with its lines and totals, the fee income and both results.
     * Also part of the event detail (EventService), which knows the fee income already.
     */
    EventBudgetDto budgetOf(Long eventId, long feeIncomeCents) {
        Map<BudgetCategoryEnum, List<BudgetLineDto>> linesByCategory = eventBudgetLineRepository.findEventLines(eventId).stream()
                .map(eventBudgetLineMapper::toBudgetLineDto)
                .collect(Collectors.groupingBy(BudgetLineDto::getCategory));
        List<BudgetCategoryDto> categories = Arrays.stream(BudgetCategoryEnum.values())
                .map(category -> toBudgetCategoryDto(category, linesByCategory.getOrDefault(category, List.of())))
                .toList();
        return toEventBudgetDto(categories, feeIncomeCents);
    }

    // ---- reading ----

    // The fees of the active participants, charged or not
    private long feeIncomeOf(Long eventId) {
        return eventParticipantRepository.findActiveTotals(List.of(eventId)).stream()
                .mapToLong(ParticipantTotal::feeCents)
                .sum();
    }


    private static BudgetCategoryDto toBudgetCategoryDto(BudgetCategoryEnum category, List<BudgetLineDto> lines) {
        return new BudgetCategoryDto(category, plannedSumOf(lines), actualSumOf(lines), lines);
    }

    private static long plannedSumOf(List<BudgetLineDto> lines) {
        return lines.stream().mapToLong(BudgetLineDto::getPlannedCents).sum();
    }

    // Lines without an actual amount add nothing
    private static long actualSumOf(List<BudgetLineDto> lines) {
        return lines.stream().map(BudgetLineDto::getActualCents).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private static EventBudgetDto toEventBudgetDto(List<BudgetCategoryDto> categories, long feeIncomeCents) {
        List<BudgetLineDto> lines = categories.stream().flatMap(category -> category.getLines().stream()).toList();
        EventBudgetDto budget = new EventBudgetDto();
        budget.setCategories(categories);
        budget.setPlannedCents(plannedSumOf(lines));
        budget.setActualCents(actualSumOf(lines));
        budget.setMissingActualCount(lines.stream().filter(line -> line.getActualCents() == null).count());
        budget.setFeeIncomeCents(feeIncomeCents);
        budget.setPlannedResultCents(feeIncomeCents - budget.getPlannedCents());
        budget.setActualResultCents(feeIncomeCents - budget.getActualCents());
        return budget;
    }

    // ---- changing ----

    private Event getValidChangeableEventBy(Long eventId) {
        Event event = eventRepository.findForUpdate(eventId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
        if (event.getStatus() == EventStatusEnum.CANCELLED) {
            throw new ForbiddenException(Error.EVENT_NOT_EDITABLE);
        }
        return event;
    }

    // A line of another event is as unknown as a missing one
    private EventBudgetLine getValidLineBy(Long eventId, Long lineId) {
        return eventBudgetLineRepository.findEventLine(eventId, lineId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private int nextSortOrder(Long eventId, BudgetCategoryEnum category) {
        return eventBudgetLineRepository.findLastSortOrder(eventId, category) + 1;
    }

    private static void normalizeTexts(EventBudgetLine line) {
        line.setDescription(line.getDescription().strip());
        line.setNote(line.getNote() == null || line.getNote().isBlank() ? null : line.getNote().strip());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
