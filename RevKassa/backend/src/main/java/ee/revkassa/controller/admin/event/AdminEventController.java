package ee.revkassa.controller.admin.event;

import ee.revkassa.controller.admin.event.dto.EventCreateRequestDto;
import ee.revkassa.controller.admin.event.dto.EventDto;
import ee.revkassa.controller.admin.event.dto.EventFeesUpdateRequestDto;
import ee.revkassa.controller.admin.event.dto.EventSummaryDto;
import ee.revkassa.controller.admin.event.dto.EventUpdateRequestDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.EventService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "All events: planned ones first (soonest first), then closed and cancelled (newest first)",
            description = "participantCount and feeIncomeCents count active participants only. "
                    + "Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<EventSummaryDto> getEvents() {
        return eventService.getEvents();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an event",
            description = "The new event is PLANNED with sign-up closed and every level's fee 0: set the fees, then "
                    + "open sign-up. startsAt may be in the past. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created, with the detail view"),
            @ApiResponse(responseCode = "400", description = "Name missing or over 100, location over 100, description "
                    + "over 2000 characters, startsAt missing (errorCode 103); malformed startsAt (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventDto createEvent(@RequestBody @Valid EventCreateRequestDto eventCreateRequest,
                                @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventService.createEvent(eventCreateRequest, principal.memberId());
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "One event with its fee per level, participant count and fee income",
            description = "fees lists every active level and deactivated levels that have a fee, in level order. "
                    + "editable is false for CLOSED and CANCELLED events. Admin password session only.")
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
    public EventDto getEvent(@PathVariable Long eventId) {
        return eventService.getEvent(eventId);
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "Change a planned event's details and sign-up switch",
            description = "The status is not changed here. Send the version from the detail view. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, with the detail view"),
            @ApiResponse(responseCode = "400", description = "Name missing or over 100, location over 100, description "
                    + "over 2000 characters, startsAt, signupOpen or version missing (errorCode 103); malformed value (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Event closed or cancelled (148), not an admin password "
                    + "session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Someone saved the event meanwhile (105): reload",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventDto updateEvent(@PathVariable Long eventId,
                                @RequestBody @Valid EventUpdateRequestDto eventUpdateRequest,
                                @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventService.updateEvent(eventId, eventUpdateRequest, principal.memberId());
    }

    @PutMapping("/{eventId}/fees")
    @Operation(summary = "Replace a planned event's fee table",
            description = "A level that is not listed pays 0. Participants added earlier keep the fee they were charged. "
                    + "Raises the event's version. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, with the detail view"),
            @ApiResponse(responseCode = "400", description = "fees or version missing, more than 100 rows, amount below 0 "
                    + "or above 10 000 €, a level listed twice (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Event closed or cancelled (148), not an admin password "
                    + "session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event or level (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Someone saved the event meanwhile (105): reload",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventDto updateFees(@PathVariable Long eventId,
                               @RequestBody @Valid EventFeesUpdateRequestDto feesUpdateRequest,
                               @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventService.updateFees(eventId, feesUpdateRequest, principal.memberId());
    }
}
