package ee.revkassa.persistence.eventparticipant;

public enum EventParticipantStatusEnum {
    ACTIVE,
    // Removed by an admin, fee returned; adding the member again makes the row ACTIVE again
    REMOVED
}
