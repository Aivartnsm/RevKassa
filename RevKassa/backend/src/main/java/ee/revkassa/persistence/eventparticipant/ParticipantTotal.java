package ee.revkassa.persistence.eventparticipant;

/**
 * Active participants of one event and the fees they were charged.
 */
public record ParticipantTotal(Long eventId, long participantCount, long feeCents) {

    public static ParticipantTotal none(Long eventId) {
        return new ParticipantTotal(eventId, 0, 0);
    }
}
