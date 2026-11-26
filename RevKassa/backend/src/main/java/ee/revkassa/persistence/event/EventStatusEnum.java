package ee.revkassa.persistence.event;

public enum EventStatusEnum {
    PLANNED,
    // After the event: read only
    CLOSED,
    // Fees were returned: read only
    CANCELLED
}
