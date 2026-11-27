package ee.revkassa.service;

import ee.revkassa.controller.admin.event.dto.EventDto;
import ee.revkassa.controller.admin.event.dto.ParticipantFeeUpdateRequestDto;
import ee.revkassa.controller.admin.event.dto.ParticipantsAddRequestDto;
import ee.revkassa.controller.admin.event.dto.ParticipantsAddResponseDto;
import ee.revkassa.controller.admin.event.dto.SkippedMemberDto;
import ee.revkassa.controller.admin.event.dto.SkippedMemberDto.SkipReasonEnum;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.event.Event;
import ee.revkassa.persistence.event.EventStatusEnum;
import ee.revkassa.persistence.eventlevelfee.EventLevelFee;
import ee.revkassa.persistence.eventlevelfee.EventLevelFeeRepository;
import ee.revkassa.persistence.eventparticipant.EventParticipant;
import ee.revkassa.persistence.eventparticipant.EventParticipantRepository;
import ee.revkassa.persistence.eventparticipant.EventParticipantStatusEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Event participants and their fees (B29). Joining copies the member's level and the fee of the price period holding
 * the join day (Tallinn date); the balance does not change until an admin charges the fees. Charging, a fee change
 * after charging, removing and cancelling write EVENT_FEE / EVENT_FEE_REVERSAL rows through AccountService, so a
 * participant's rows always sum to -feeCents when active and charged, otherwise to 0.
 * Every change locks the event row first, so changes to one event (and its status) run one after another.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventParticipantService {

    private final EventService eventService;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventLevelFeeRepository eventLevelFeeRepository;
    private final MemberRepository memberRepository;
    private final AccountService accountService;
    private final Clock clock;

    /**
     * Inactive members and members already taking part are skipped; a removed member joins again. An unknown member
     * adds nobody.
     */
    @Transactional
    public ParticipantsAddResponseDto addParticipants(Long eventId, ParticipantsAddRequestDto request, Long adminId) {
        Event event = eventService.getValidEventForUpdateBy(eventId);
        EventService.validateEditable(event);
        List<Long> memberIds = request.getMemberIds().stream().distinct().toList();
        Map<Long, Member> members = getValidMembersBy(memberIds);
        Map<Long, EventParticipant> existing = eventParticipantRepository.findByMembers(eventId, memberIds).stream()
                .collect(Collectors.toMap(participant -> participant.getMember().getId(), Function.identity()));
        OffsetDateTime now = now();
        List<EventLevelFee> fees = eventLevelFeeRepository.findEventFees(eventId);
        Member admin = memberRepository.getReferenceById(adminId);
        List<SkippedMemberDto> skipped = new ArrayList<>();
        for (Long memberId : memberIds) {
            Member member = members.get(memberId);
            EventParticipant participant = existing.get(memberId);
            SkipReasonEnum reason = skipReasonOf(member, participant);
            if (reason != null) {
                skipped.add(new SkippedMemberDto(memberId, reason));
                continue;
            }
            join(event, member, participant, feeOf(member, fees, now), admin, now);
        }
        log.info("Event participants added: eventId={}, added={}, skipped={}, adminId={}",
                eventId, memberIds.size() - skipped.size(), skipped.size(), adminId);
        return new ParticipantsAddResponseDto(eventService.getEvent(eventId), skipped);
    }

    /**
     * "Võta tasud": every active participant not charged yet. A fee of 0 writes no row but counts as charged.
     */
    @Transactional
    public EventDto chargeFees(Long eventId, Long adminId) {
        Event event = eventService.getValidEventForUpdateBy(eventId);
        EventService.validatePublished(event);
        int charged = chargeRemainingFees(eventId, adminId);
        log.info("Event fees charged: eventId={}, participants={}, adminId={}", eventId, charged, adminId);
        return eventService.getEvent(eventId);
    }

    /**
     * Before charging only the fee changes; after it the difference goes to the balance, with the note.
     */
    @Transactional
    public EventDto changeFee(Long eventId, Long memberId, ParticipantFeeUpdateRequestDto request, Long adminId) {
        Event event = eventService.getValidEventForUpdateBy(eventId);
        EventService.validateEditable(event);
        EventParticipant participant = getValidActiveParticipantBy(eventId, memberId);
        long difference = request.getFeeCents() - participant.getFeeCents();
        if (participant.isCharged() && difference != 0) {
            postFeeDifference(participant, difference, request.getNote(), memberRepository.getReferenceById(adminId));
        }
        participant.setFeeCents(request.getFeeCents());
        log.info("Event participant fee changed: eventId={}, memberId={}, charged={}, adminId={}",
                eventId, memberId, participant.isCharged(), adminId);
        return eventService.getEvent(eventId);
    }

    /**
     * The row stays, as REMOVED; a charged fee goes back to the balance.
     */
    @Transactional
    public EventDto removeParticipant(Long eventId, Long memberId, Long adminId) {
        Event event = eventService.getValidEventForUpdateBy(eventId);
        EventService.validateEditable(event);
        EventParticipant participant = getValidActiveParticipantBy(eventId, memberId);
        remove(participant, memberRepository.getReferenceById(adminId), now());
        log.info("Event participant removed: eventId={}, memberId={}, adminId={}", eventId, memberId, adminId);
        return eventService.getEvent(eventId);
    }

    /**
     * After the event: the remaining fees are charged and nothing can be changed any more. Sign-up closes.
     */
    @Transactional
    public EventDto closeEvent(Long eventId, Long adminId) {
        Event event = eventService.getValidEventForUpdateBy(eventId);
        EventService.validatePublished(event);
        int charged = chargeRemainingFees(eventId, adminId);
        event.setSignupOpen(false);
        eventService.changeStatus(event, EventStatusEnum.CLOSED);
        log.info("Event closed: eventId={}, charged={}, adminId={}", eventId, charged, adminId);
        return eventService.getEvent(eventId);
    }

    /**
     * Every active participant is removed and charged fees go back. Sign-up closes.
     */
    @Transactional
    public EventDto cancelEvent(Long eventId, Long adminId) {
        Event event = eventService.getValidEventForUpdateBy(eventId);
        EventService.validateEditable(event);
        List<EventParticipant> active = eventParticipantRepository.findActive(eventId);
        Member admin = memberRepository.getReferenceById(adminId);
        OffsetDateTime now = now();
        active.forEach(participant -> remove(participant, admin, now));
        event.setSignupOpen(false);
        eventService.changeStatus(event, EventStatusEnum.CANCELLED);
        log.info("Event cancelled: eventId={}, removed={}, adminId={}", eventId, active.size(), adminId);
        return eventService.getEvent(eventId);
    }

    // ---- adding ----

    private Map<Long, Member> getValidMembersBy(List<Long> memberIds) {
        Map<Long, Member> members = memberRepository.findWithLevels(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        if (members.size() != memberIds.size()) {
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
        return members;
    }

    private static SkipReasonEnum skipReasonOf(Member member, EventParticipant participant) {
        if (!member.getActive()) {
            return SkipReasonEnum.MEMBER_NOT_ACTIVE;
        }
        if (participant != null && participant.isActive()) {
            return SkipReasonEnum.ALREADY_PARTICIPANT;
        }
        return null;
    }

    /**
     * The member's level's amount in the price period holding the join day: the latest period starting on that day or
     * before (the one from the start has no date). A level without a row in that period pays 0.
     */
    private static long feeOf(Member member, List<EventLevelFee> fees, OffsetDateTime joinedAt) {
        LocalDate joinDay = joinedAt.atZoneSameInstant(ClockConfig.APP_ZONE).toLocalDate();
        List<EventLevelFee> started = fees.stream()
                .filter(fee -> fee.getValidFrom() == null || !fee.getValidFrom().isAfter(joinDay))
                .toList();
        LocalDate periodStart = started.stream()
                .map(EventLevelFee::getValidFrom)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        return started.stream()
                .filter(fee -> Objects.equals(fee.getValidFrom(), periodStart))
                .filter(fee -> fee.getLevel().getId().equals(member.getLevel().getId()))
                .mapToLong(EventLevelFee::getAmountCents)
                .findFirst()
                .orElse(0);
    }

    // A removed member's row becomes active again, as if they joined now
    private void join(Event event, Member member, EventParticipant removed, long feeCents, Member admin,
                      OffsetDateTime now) {
        EventParticipant participant = removed == null ? new EventParticipant() : removed;
        participant.setEvent(event);
        participant.setMember(member);
        participant.setLevel(member.getLevel());
        participant.setFeeCents(feeCents);
        participant.setStatus(EventParticipantStatusEnum.ACTIVE);
        participant.setAddedBy(admin);
        participant.setAddedAt(now);
        participant.setRemovedAt(null);
        participant.setChargedAt(null);
        eventParticipantRepository.save(participant);
    }

    // ---- charging ----

    private int chargeRemainingFees(Long eventId, Long adminId) {
        List<EventParticipant> uncharged = eventParticipantRepository.findUncharged(eventId);
        Member admin = memberRepository.getReferenceById(adminId);
        OffsetDateTime now = now();
        for (EventParticipant participant : uncharged) {
            if (participant.getFeeCents() > 0) {
                accountService.post(AccountPosting.eventFee(participant, participant.getFeeCents(), null, admin));
            }
            participant.setChargedAt(now);
        }
        return uncharged.size();
    }

    // ---- changing one participant ----

    private EventParticipant getValidActiveParticipantBy(Long eventId, Long memberId) {
        EventParticipant participant = eventParticipantRepository.findByMember(eventId, memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
        if (!participant.isActive()) {
            throw new ForbiddenException(Error.PARTICIPANT_NOT_ACTIVE);
        }
        return participant;
    }

    private void postFeeDifference(EventParticipant participant, long difference, String note, Member admin) {
        if (difference > 0) {
            accountService.post(AccountPosting.eventFee(participant, difference, note, admin));
        } else {
            accountService.post(AccountPosting.eventFeeReversal(participant, -difference, note, admin));
        }
    }

    private void remove(EventParticipant participant, Member admin, OffsetDateTime now) {
        if (participant.isCharged() && participant.getFeeCents() > 0) {
            accountService.post(AccountPosting.eventFeeReversal(participant, participant.getFeeCents(), null, admin));
        }
        participant.setStatus(EventParticipantStatusEnum.REMOVED);
        participant.setRemovedAt(now);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
