package ee.revkassa.persistence.event;

public enum EventStatusEnum {
    // Being prepared: members do not see it
    DRAFT,
    // Members see it; they can sign up while signupOpen is on
    PUBLISHED,
    // After the event, fees charged: read only
    CLOSED,
    // Charged fees were returned: read only
    CANCELLED
}
