package ee.revkassa.controller.event;

import ee.revkassa.controller.event.dto.MemberEventDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.MemberEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Events for the logged-in member. There is deliberately no way to cancel a sign-up here: only an admin removes
 * participants.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final MemberEventService memberEventService;

    @GetMapping
    @Operation(summary = "Your events: published ones from today on, earlier ones you take part in, your last 10 closed",
            description = "Published events soonest first, then closed ones newest first. Drafts and cancelled events "
                    + "are never listed. feeCents: your copied fee when signed up, otherwise your level's fee today; "
                    + "nextFee: the next date your fee would change (null when none or signed up). Any logged-in "
                    + "member (PIN or password).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member deactivated (112)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<MemberEventDto> getMyEvents(@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return memberEventService.getEvents(principal.memberId());
    }

    @PostMapping("/{eventId}/signup")
    @Operation(summary = "Sign up for a published event whose sign-up is open",
            description = "Your level and its fee in today's price period are copied; the fee goes to your balance "
                    + "when an admin charges the event's fees. A member an admin removed may sign up again. Only in a "
                    + "password session, not with a PIN on the bar tablet. Cannot be undone by the member. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signed up, with the event card"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Sign-up closed, or event closed or cancelled (153); "
                    + "already signed up (154); PIN session (155); member deactivated (112); CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such event, or a draft (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MemberEventDto signUp(@PathVariable Long eventId,
                                 @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return memberEventService.signUp(eventId, principal.memberId(), principal.loginMethod());
    }
}
