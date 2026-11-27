package ee.revkassa.controller.admin.assistant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * question is required for QUESTION and an optional wish for the quick actions. It is sent to Google: no names.
 */
@Data
public class AssistantQuestionRequestDto implements Serializable {

    public static final int MAX_QUESTION_LENGTH = 1000;

    @NotNull
    private AssistantKindEnum kind;

    @Size(max = MAX_QUESTION_LENGTH)
    private String question;
}
