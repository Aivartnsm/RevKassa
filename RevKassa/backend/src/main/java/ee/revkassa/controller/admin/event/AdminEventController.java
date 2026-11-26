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
    @Operation(summary = "All events: draft and published ones first (soonest first), then closed and cancelled (newest first)",
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
            description = "The new event is a DRAFT (members do not see it) with sign-up closed and every level's fee 0: "
                    + "set the fees, publish, then open sign-up. startsAt may be in the past. Needs the X-XSRF-TOKEN header.")
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
    @Operation(summary = "One event with its fee table, participant count and fee income",
            description = "feePeriods: the period from the start (validFrom null) first, then by date; each lists every "
                    + "active level and deactivated levels that have a fee, in level order. editable is false for CLOSED "
                    + "and CANCELLED events. Admin password session only.")
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
    @Operation(summary = "Change a draft or published event's details and sign-up switch",
            description = "The status is not changed here. Sign-up can only be opened on a published event. Send the "
                    + "version from the detail view. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, with the detail view"),
            @ApiResponse(responseCode = "400", description = "Name missing or over 100, location over 100, description "
                    + "over 2000 characters, startsAt, signupOpen or version missing (errorCode 103); malformed value (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Event closed or cancelled (148), sign-up opened on an "
                    + "unpublished event (150), not an admin password session or CSRF token missing (118)",
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
    @Operation(summary = "Replace a draft or published event's fee table: price periods with a fee per level",
            description = "periods: [{validFrom (null = from the start), fees: [{levelId, amountCents}]}]. A participant "
                    + "pays their level's amount in the period holding the day they joined; a level not listed there "
                    + "pays 0. Participants who already joined keep their fee. Raises the event's version. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, with the detail view"),
            @ApiResponse(responseCode = "400", description = "periods or version missing, more than 10 periods or 100 "
                    + "levels in one, amount below 0 or above 10 000 €, a level listed twice in a period, two periods "
                    + "starting on the same day (errorCode 103); malformed date (104)",
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

    @PostMapping("/{eventId}/publish")
    @Operation(summary = "Publish a draft event: members see it from now on",
            description = "Sign-up stays closed until it is switched on with PUT. Raises the event's version. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Published, with the detail view"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Already published (149), closed or cancelled (148), not an "
                    + "admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventDto publishEvent(@PathVariable Long eventId,
                                 @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventService.publishEvent(eventId, principal.memberId());
    }

    @PostMapping("/{eventId}/unpublish")
    @Operation(summary = "Turn a published event back into a draft, while nobody takes part",
            description = "For an event published by mistake. Sign-up closes. Raises the event's version. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Back to draft, with the detail view"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not published (150), has active participants (151), closed "
                    + "or cancelled (148), not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventDto unpublishEvent(@PathVariable Long eventId,
                                   @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventService.unpublishEvent(eventId, principal.memberId());
    }
}
