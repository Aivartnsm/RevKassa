package ee.revkassa.controller.admin.event;

import ee.revkassa.controller.admin.event.dto.EventDto;
import ee.revkassa.controller.admin.event.dto.ParticipantFeeUpdateRequestDto;
import ee.revkassa.controller.admin.event.dto.ParticipantsAddRequestDto;
import ee.revkassa.controller.admin.event.dto.ParticipantsAddResponseDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.EventParticipantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events/{eventId}/participants")
@RequiredArgsConstructor
public class AdminEventParticipantController {

    private final EventParticipantService eventParticipantService;

    @PostMapping
    @Operation(summary = "Add members to a draft or published event",
            description = "Each member joins with their current level and the fee of the price period holding today "
                    + "(Tallinn date); the balance does not change until the fees are charged. A member listed twice is "
                    + "added once. Inactive members (MEMBER_NOT_ACTIVE) and members already taking part "
                    + "(ALREADY_PARTICIPANT) are skipped and listed in skipped; a removed member joins again. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Added, with the detail view and the skipped members"),
            @ApiResponse(responseCode = "400", description = "memberIds missing, empty, over 200 or with an id not above "
                    + "0 (errorCode 103); malformed value (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Event closed or cancelled (148), not an admin password "
                    + "session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event, or a member does not exist: nobody is "
                    + "added (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ParticipantsAddResponseDto addParticipants(@PathVariable Long eventId,
                                                      @RequestBody @Valid ParticipantsAddRequestDto participantsAddRequest,
                                                      @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventParticipantService.addParticipants(eventId, participantsAddRequest, principal.memberId());
    }

    @PutMapping("/{memberId}/fee")
    @Operation(summary = "Change an active participant's fee",
            description = "Before the fee is charged only the amount changes. After it the difference goes to the "
                    + "balance (EVENT_FEE or EVENT_FEE_REVERSAL) with the note, which the member sees in their history. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, with the detail view"),
            @ApiResponse(responseCode = "400", description = "feeCents missing, below 0 or above 10 000 €, note over "
                    + "500 characters (errorCode 103); malformed value (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Participant already removed (152), event closed or "
                    + "cancelled (148), not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event, or the member is not a participant (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventDto changeFee(@PathVariable Long eventId, @PathVariable Long memberId,
                              @RequestBody @Valid ParticipantFeeUpdateRequestDto feeUpdateRequest,
                              @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventParticipantService.changeFee(eventId, memberId, feeUpdateRequest, principal.memberId());
    }

    @DeleteMapping("/{memberId}")
    @Operation(summary = "Remove a participant",
            description = "The row stays as REMOVED. A charged fee goes back to the balance (EVENT_FEE_REVERSAL). "
                    + "Adding the member again later makes them a participant again. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Removed, with the detail view"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Already removed (152), event closed or cancelled (148), "
                    + "not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event, or the member is not a participant (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public EventDto removeParticipant(@PathVariable Long eventId, @PathVariable Long memberId,
                                      @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return eventParticipantService.removeParticipant(eventId, memberId, principal.memberId());
    }
}
