package ee.revkassa.controller.admin.assistant;

import ee.revkassa.controller.admin.assistant.dto.AssistantStatusDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.service.EventAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assistant")
@RequiredArgsConstructor
public class AdminAssistantController {

    private final EventAssistantService eventAssistantService;

    @GetMapping("/status")
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
}
