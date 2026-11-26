package ee.revkassa.service;

import ee.revkassa.controller.admin.membershiplevel.dto.MembershipLevelCreateRequestDto;
import ee.revkassa.controller.admin.membershiplevel.dto.MembershipLevelDto;
import ee.revkassa.controller.admin.membershiplevel.dto.MembershipLevelUpdateRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.membershiplevel.LevelMemberCount;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.persistence.membershiplevel.MembershipLevelMapper;
import ee.revkassa.persistence.membershiplevel.MembershipLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Membership levels decide the monthly fee and event fees. A level that anything refers to (a member, a fee, a charge,
 * an event) is never deleted, only deactivated: hidden from new choices while its members keep it.
 * At least one level always stays active, because a new member gets the first active one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipLevelService {

    private final MembershipLevelRepository membershipLevelRepository;
    private final MembershipLevelMapper membershipLevelMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MembershipLevelDto> getLevels() {
        Map<Long, Long> memberCounts = membershipLevelRepository.countActiveMembersByLevel().stream()
                .collect(Collectors.toMap(LevelMemberCount::levelId, LevelMemberCount::memberCount));
        Set<Long> usedLevelIds = new HashSet<>(membershipLevelRepository.findUsedLevelIds());
        return membershipLevelRepository.findAllLevels().stream()
                .map(level -> toMembershipLevelDto(level, memberCounts.getOrDefault(level.getId(), 0L),
                        !usedLevelIds.contains(level.getId())))
                .toList();
    }

    @Transactional
    public MembershipLevelDto createLevel(MembershipLevelCreateRequestDto request, Long adminId) {
        String name = request.getName().strip();
        validateNameFree(name, null);
        MembershipLevel level = membershipLevelRepository.save(createLevel(name));
        log.info("Membership level created: levelId={}, adminId={}", level.getId(), adminId);
        return toMembershipLevelDto(level, 0L, true);
    }

    @Transactional
    public MembershipLevelDto updateLevel(Long levelId, MembershipLevelUpdateRequestDto request, Long adminId) {
        MembershipLevel level = getValidLevelBy(levelId);
        String name = request.getName().strip();
        validateNameFree(name, levelId);
        if (level.getActive() && !request.getActive()) {
            validateAnotherActiveLevelExists(level);
        }
        membershipLevelMapper.updateMembershipLevel(request, level);
        level.setName(name);
        membershipLevelRepository.flush();
        log.info("Membership level updated: levelId={}, sortOrder={}, active={}, adminId={}",
                levelId, level.getSortOrder(), level.getActive(), adminId);
        return toMembershipLevelDto(level, membershipLevelRepository.countActiveMembers(levelId),
                !membershipLevelRepository.isInUse(levelId));
    }

    @Transactional
    public void deleteLevel(Long levelId, Long adminId) {
        MembershipLevel level = getValidLevelBy(levelId);
        if (membershipLevelRepository.isInUse(levelId)) {
            throw new ForbiddenException(Error.LEVEL_IN_USE);
        }
        if (level.getActive()) {
            validateAnotherActiveLevelExists(level);
        }
        membershipLevelRepository.delete(level);
        log.info("Membership level deleted: levelId={}, adminId={}", levelId, adminId);
    }

    // ---- helpers ----

    private MembershipLevelDto toMembershipLevelDto(MembershipLevel level, long memberCount, boolean deletable) {
        MembershipLevelDto dto = membershipLevelMapper.toMembershipLevelDto(level);
        dto.setMemberCount(memberCount);
        dto.setDeletable(deletable);
        return dto;
    }

    // Case does not matter, like the unique index on lower(name)
    private void validateNameFree(String name, Long ownLevelId) {
        membershipLevelRepository.findByName(name)
                .filter(other -> !other.getId().equals(ownLevelId))
                .ifPresent(other -> {
                    throw new ForbiddenException(Error.LEVEL_NAME_TAKEN);
                });
    }

    private MembershipLevel createLevel(String name) {
        MembershipLevel level = new MembershipLevel();
        level.setName(name);
        level.setSortOrder(membershipLevelRepository.findMaxSortOrder() + 1);
        level.setActive(true);
        level.setCreatedAt(OffsetDateTime.now(clock));
        return level;
    }

    private MembershipLevel getValidLevelBy(Long levelId) {
        return membershipLevelRepository.findById(levelId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Without an active level no member could be added (a new member gets the first active one)
    private void validateAnotherActiveLevelExists(MembershipLevel level) {
        boolean otherActiveLevelExists = membershipLevelRepository.findAllActiveForUpdate().stream()
                .anyMatch(active -> !active.getId().equals(level.getId()));
        if (!otherActiveLevelExists) {
            throw new ForbiddenException(Error.LAST_ACTIVE_LEVEL);
        }
    }
}
