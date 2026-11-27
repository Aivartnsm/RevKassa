package ee.revkassa.service;

import ee.revkassa.controller.event.dto.NextFeeDto;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.persistence.eventlevelfee.EventLevelFee;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One event's fee table: price periods (from a Tallinn date on; the one from the start has no date) with an amount
 * per level. A day belongs to the latest period starting on it or before; a level without a row in a period pays 0.
 * Used when a member joins (admin adds, B29, or signs up, B33) and on the member's event cards.
 */
final class EventFeeTable {

    private final List<EventLevelFee> rows;

    private EventFeeTable(List<EventLevelFee> rows) {
        this.rows = rows;
    }

    static EventFeeTable of(List<EventLevelFee> rows) {
        return new EventFeeTable(rows);
    }

    // The Tallinn date a moment falls on: price periods start on club dates
    static LocalDate dayOf(OffsetDateTime moment) {
        return moment.atZoneSameInstant(ClockConfig.APP_ZONE).toLocalDate();
    }

    long feeOn(LocalDate day, Long levelId) {
        return amountIn(periodStartOn(day), levelId);
    }

    /**
     * The first later period in which the level's amount differs from today's, if any (a rise or a drop).
     */
    Optional<NextFeeDto> nextChangeAfter(LocalDate day, Long levelId) {
        long current = feeOn(day, levelId);
        return rows.stream()
                .map(EventLevelFee::getValidFrom)
                .filter(Objects::nonNull)
                .filter(validFrom -> validFrom.isAfter(day))
                .distinct()
                .sorted()
                .filter(validFrom -> amountIn(validFrom, levelId) != current)
                .findFirst()
                .map(validFrom -> new NextFeeDto(validFrom, amountIn(validFrom, levelId)));
    }

    // null = the period from the start
    private LocalDate periodStartOn(LocalDate day) {
        return rows.stream()
                .map(EventLevelFee::getValidFrom)
                .filter(Objects::nonNull)
                .filter(validFrom -> !validFrom.isAfter(day))
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private long amountIn(LocalDate periodStart, Long levelId) {
        return rows.stream()
                .filter(row -> Objects.equals(row.getValidFrom(), periodStart))
                .filter(row -> row.getLevel().getId().equals(levelId))
                .mapToLong(EventLevelFee::getAmountCents)
                .findFirst()
                .orElse(0);
    }
}
