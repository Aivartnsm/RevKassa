package ee.revkassa.controller.admin.assistant;

import ee.revkassa.controller.admin.assistant.dto.AssistantAnswerDto;
import ee.revkassa.controller.admin.assistant.dto.AssistantQuestionRequestDto;
import ee.revkassa.controller.admin.assistant.dto.AssistantStatusDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.EventAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAssistantController {

    private final EventAssistantService eventAssistantService;

    @GetMapping("/assistant/status")
    @Operation(summary = "Whether the AI event assistant is on",
            description = "On only when the server has a Gemini API key (GOOGLE_API_KEY). It says nothing about funds "
                    + "or quota: a failed question answers 503 (157). Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AssistantStatusDto getStatus() {
        return eventAssistantService.getStatus();
    }

    @PostMapping("/events/{eventId}/assistant")
    @Operation(summary = "Ask the AI event assistant about one event",
            description = "kind: BUDGET (budget draft), QUANTITIES (how much to buy), RECIPE (the assistant picks a dish "
                    + "for the active participants, 10 people when there are none), QUESTION (question required). "
                    + "Only event data is sent to Google: name, start, description, participant counts per level and "
                    + "budget lines without notes - never names, emails, balances or the location. Nothing is saved: "
                    + "add a suggestion with POST .../budget-lines and source AI. answer is model output, show it as "
                    + "plain text. structured false: the reply was not valid JSON, answer is its raw text. "
                    + "20 questions per admin per Tallinn day; every question sent counts, also a failed one. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Answered, with the questions left today"),
            @ApiResponse(responseCode = "400", description = "kind missing, question over 1000 characters or missing "
                    + "for QUESTION (errorCode 103); unknown kind, malformed id (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Event cancelled (148), not an admin password session or "
                    + "CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Daily limit used up (158); Retry-After: seconds until "
                    + "Tallinn midnight",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Assistant not configured (156), or it did not answer: "
                    + "error, timeout, blocked reply or funds used up (157)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AssistantAnswerDto ask(@PathVariable Long eventId,
                                  @RequestBody @Valid AssistantQuestionRequestDto questionRequest,
                                  @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventAssistantService.ask(eventId, questionRequest, principal.memberId());
    }
}
