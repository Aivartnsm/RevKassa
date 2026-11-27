package ee.revkassa.controller.admin.assistant.dto;

/**
 * The panel's quick actions and the free question.
 */
public enum AssistantKindEnum {
    // "Eelarve mustand"
    BUDGET,
    // "Kui palju osta"
    QUANTITIES,
    // "Retsept N inimesele": the assistant picks the dish, N = active participants
    RECIPE,
    QUESTION
}
