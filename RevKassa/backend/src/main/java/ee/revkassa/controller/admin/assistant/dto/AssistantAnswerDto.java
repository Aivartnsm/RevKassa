package ee.revkassa.controller.admin.assistant.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Model output: the UI shows it as plain text only, never as HTML.
 */
@Data
public class AssistantAnswerDto implements Serializable {
    private String answer;
    // Valid suggestions only, at most 10; empty when none or when the reply was not valid JSON
    private List<SuggestedLineDto> suggestedLines;
    // false: the reply was not valid JSON (e.g. cut off at the token limit), answer is its raw text
    private boolean structured;
    // Questions this admin has left today (Tallinn day)
    private int remainingToday;
}
