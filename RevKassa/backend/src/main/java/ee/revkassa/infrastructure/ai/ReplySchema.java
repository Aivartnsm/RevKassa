package ee.revkassa.infrastructure.ai;

import ee.revkassa.persistence.eventbudgetline.BudgetCategoryEnum;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The JSON every assistant reply must have (JSON Schema, sent to the API so it answers in this shape):
 * {answer, suggestedLines: [{category, description, plannedCents}]}. Accepted suggestions become budget lines.
 */
public final class ReplySchema {

    public static final Map<String, Object> JSON_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "answer", Map.of("type", "string"),
                    "suggestedLines", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "category", Map.of("type", "string", "enum", categories()),
                                            "description", Map.of("type", "string"),
                                            "plannedCents", Map.of("type", "integer", "minimum", 0)),
                                    "required", List.of("category", "description", "plannedCents")))),
            "required", List.of("answer", "suggestedLines"));

    private ReplySchema() {
    }

    private static List<String> categories() {
        return Arrays.stream(BudgetCategoryEnum.values()).map(Enum::name).toList();
    }
}
