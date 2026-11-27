package ee.revkassa.service;

import ee.revkassa.controller.admin.assistant.dto.AssistantAnswerDto;
import ee.revkassa.controller.admin.assistant.dto.AssistantKindEnum;
import ee.revkassa.controller.admin.assistant.dto.AssistantQuestionRequestDto;
import ee.revkassa.controller.admin.assistant.dto.AssistantStatusDto;
import ee.revkassa.infrastructure.ai.AssistantReply;
import ee.revkassa.infrastructure.ai.AssistantUnavailableException;
import ee.revkassa.infrastructure.ai.EventAssistant;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The admin's event assistant (B31: whether it is on; B32: asking it about one event). Only event data is sent
 * (EventAssistantPromptBuilder), each admin has a daily allowance (AssistantDailyLimit), and the reply is checked
 * before the admin sees it (AssistantAnswerParser). Nothing is saved: the admin adds suggestions to the budget
 * themselves. The log has who, which event and the tokens, never the prompt or the answer.
 * Not transactional: the API call can take up to a minute and must not hold a database connection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAssistantService {

    private final EventAssistant eventAssistant;
    private final EventAssistantPromptBuilder promptBuilder;
    private final AssistantDailyLimit dailyLimit;
    private final AssistantAnswerParser answerParser;

    public AssistantStatusDto getStatus() {
        return new AssistantStatusDto(eventAssistant.isEnabled());
    }

    /**
     * Refusals before sending (invalid question, assistant off, no such or a cancelled event) do not count towards
     * the daily limit; everything sent does, also when the API then fails.
     */
    public AssistantAnswerDto ask(Long eventId, AssistantQuestionRequestDto request, Long adminId) {
        String question = questionOf(request);
        if (!eventAssistant.isEnabled()) {
            throw new AssistantUnavailableException(Error.ASSISTANT_DISABLED);
        }
        AssistantPrompt prompt = promptBuilder.build(eventId, request.getKind(), question);
        int remainingToday = dailyLimit.take(adminId);
        AssistantReply reply = send(prompt, eventId, request.getKind(), adminId);
        AssistantAnswerDto answer = answerParser.parse(reply.text());
        answer.setRemainingToday(remainingToday);
        log.info("Event assistant answered: adminId={}, eventId={}, kind={}, inputTokens={}, outputTokens={}, structured={}",
                adminId, eventId, request.getKind(), reply.inputTokens(), reply.outputTokens(), answer.isStructured());
        return answer;
    }

    // Required for a free question; an optional wish for the quick actions
    private static String questionOf(AssistantQuestionRequestDto request) {
        String question = request.getQuestion() == null || request.getQuestion().isBlank()
                ? null : request.getQuestion().strip();
        if (question == null && request.getKind() == AssistantKindEnum.QUESTION) {
            throw new BadRequestException(Error.INVALID_DATA_IN_FIELDS);
        }
        return question;
    }

    private AssistantReply send(AssistantPrompt prompt, Long eventId, AssistantKindEnum kind, Long adminId) {
        try {
            return eventAssistant.ask(prompt.system(), prompt.user());
        } catch (AssistantUnavailableException e) {
            log.warn("Event assistant did not answer: adminId={}, eventId={}, kind={}", adminId, eventId, kind);
            throw e;
        }
    }
}
