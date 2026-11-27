package ee.revkassa.service;

import ee.revkassa.controller.admin.assistant.dto.AssistantAnswerDto;
import ee.revkassa.controller.admin.assistant.dto.SuggestedLineDto;
import ee.revkassa.controller.admin.event.dto.BudgetLineUpdateRequestDto;
import ee.revkassa.persistence.eventbudgetline.BudgetCategoryEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Turns the assistant's reply into the panel's answer. The reply is model output: it is checked, not trusted. Invalid
 * JSON (e.g. cut off at the token limit) gives the raw text without suggestions. Each suggestion must be a valid
 * budget line (known category, description, amount within the budget's limits) or it is dropped; at most 10 stay.
 */
@Component
@RequiredArgsConstructor
class AssistantAnswerParser {

    static final int MAX_SUGGESTIONS = 10;
    private static final Set<String> CATEGORIES = Arrays.stream(BudgetCategoryEnum.values())
            .map(Enum::name).collect(Collectors.toSet());

    private final JsonMapper jsonMapper;

    AssistantAnswerDto parse(String replyText) {
        return readObject(replyText)
                .filter(reply -> isText(reply.path("answer")))
                .map(AssistantAnswerParser::structured)
                .orElseGet(() -> rawText(replyText));
    }

    private Optional<JsonNode> readObject(String replyText) {
        try {
            JsonNode node = jsonMapper.readTree(replyText);
            return node != null && node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    private static AssistantAnswerDto structured(JsonNode reply) {
        List<SuggestedLineDto> lines = StreamSupport.stream(reply.path("suggestedLines").values().spliterator(), false)
                .map(AssistantAnswerParser::toSuggestedLine)
                .filter(Objects::nonNull)
                .limit(MAX_SUGGESTIONS)
                .toList();
        return answer(reply.path("answer").stringValue().strip(), lines, true);
    }

    // null = not a valid budget line
    private static SuggestedLineDto toSuggestedLine(JsonNode line) {
        JsonNode category = line.path("category");
        JsonNode description = line.path("description");
        JsonNode plannedCents = line.path("plannedCents");
        if (!isText(category) || !CATEGORIES.contains(category.stringValue()) || !isText(description)
                || !plannedCents.isIntegralNumber() || !plannedCents.canConvertToLong()) {
            return null;
        }
        long cents = plannedCents.longValue();
        if (cents < 0 || cents > BudgetLineUpdateRequestDto.MAX_AMOUNT_CENTS) {
            return null;
        }
        return new SuggestedLineDto(BudgetCategoryEnum.valueOf(category.stringValue()),
                shortened(description.stringValue().strip()), cents);
    }

    private static boolean isText(JsonNode node) {
        return node.isString() && !node.stringValue().isBlank();
    }

    private static String shortened(String description) {
        int max = BudgetLineUpdateRequestDto.MAX_DESCRIPTION_LENGTH;
        return description.length() <= max ? description : description.substring(0, max).strip();
    }

    private static AssistantAnswerDto rawText(String replyText) {
        return answer(replyText.strip(), List.of(), false);
    }

    private static AssistantAnswerDto answer(String text, List<SuggestedLineDto> lines, boolean structured) {
        AssistantAnswerDto answer = new AssistantAnswerDto();
        answer.setAnswer(text);
        answer.setSuggestedLines(lines);
        answer.setStructured(structured);
        return answer;
    }
}
