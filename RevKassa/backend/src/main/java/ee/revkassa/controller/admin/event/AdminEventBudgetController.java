package ee.revkassa.controller.admin.event;

import ee.revkassa.controller.admin.event.dto.BudgetLineCreateRequestDto;
import ee.revkassa.controller.admin.event.dto.BudgetLineUpdateRequestDto;
import ee.revkassa.controller.admin.event.dto.EventBudgetDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.EventBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events/{eventId}/budget-lines")
@RequiredArgsConstructor
public class AdminEventBudgetController {

    private final EventBudgetService eventBudgetService;

    @GetMapping
    @Operation(summary = "The event's budget: lines per category with totals, fee income and results",
            description = "Every category is listed in a fixed order (DRINKS, SNACKS, FOOD, ENTERTAINMENT, VENUE, "
                    + "DECORATION, OTHER), also without lines. actualCents sums only filled-in actual amounts; "
                    + "missingActualCount counts the lines without one. feeIncomeCents: the active participants' fees, "
                    + "charged or not. plannedResultCents = income - planned, actualResultCents = income - actual. "
                    + "Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventBudgetDto getBudget(@PathVariable Long eventId) {
        return eventBudgetService.getBudget(eventId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a budget line",
            description = "The line goes last in its category. source: AI for an accepted assistant suggestion, "
                    + "MANUAL when missing; it cannot be changed later. Allowed on every event except a cancelled one. "
                    + "Never touches balances or stock. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created, with the whole budget"),
            @ApiResponse(responseCode = "400", description = "category or plannedCents missing, description missing or "
                    + "over 200 characters, an amount below 0 or above 10 000 €, note over 500 characters "
                    + "(errorCode 103); unknown category or source, malformed value (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Event cancelled (148), not an admin password session or "
                    + "CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventBudgetDto createLine(@PathVariable Long eventId,
                                     @RequestBody @Valid BudgetLineCreateRequestDto lineCreateRequest,
                                     @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventBudgetService.createLine(eventId, lineCreateRequest, principal.memberId());
    }

    @PutMapping("/{lineId}")
    @Operation(summary = "Change a budget line, e.g. fill in its actual amount",
            description = "A line moved to another category goes last there. Allowed on every event except a "
                    + "cancelled one, so actual amounts can be filled in after closing. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, with the whole budget"),
            @ApiResponse(responseCode = "400", description = "category or plannedCents missing, description missing or "
                    + "over 200 characters, an amount below 0 or above 10 000 €, note over 500 characters "
                    + "(errorCode 103); unknown category, malformed value (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Event cancelled (148), not an admin password session or "
                    + "CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event, or no such line on this event (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventBudgetDto updateLine(@PathVariable Long eventId, @PathVariable Long lineId,
                                     @RequestBody @Valid BudgetLineUpdateRequestDto lineUpdateRequest,
                                     @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventBudgetService.updateLine(eventId, lineId, lineUpdateRequest, principal.memberId());
    }

    @DeleteMapping("/{lineId}")
    @Operation(summary = "Delete a budget line",
            description = "Planning data, so the line is really deleted. Allowed on every event except a cancelled one. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted, with the whole budget"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Event cancelled (148), not an admin password session or "
                    + "CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event, or no such line on this event (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventBudgetDto deleteLine(@PathVariable Long eventId, @PathVariable Long lineId,
                                     @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventBudgetService.deleteLine(eventId, lineId, principal.memberId());
    }
}
