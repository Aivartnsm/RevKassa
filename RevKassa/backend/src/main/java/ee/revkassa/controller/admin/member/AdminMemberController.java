package ee.revkassa.controller.admin.member;

import ee.revkassa.controller.admin.member.dto.AdjustmentCreateRequestDto;
import ee.revkassa.controller.admin.member.dto.AdminMemberDto;
import ee.revkassa.controller.admin.member.dto.MemberCreateRequestDto;
import ee.revkassa.controller.admin.member.dto.MemberCreatedDto;
import ee.revkassa.controller.admin.member.dto.MemberPinDto;
import ee.revkassa.controller.admin.member.dto.MemberPurchasePageDto;
import ee.revkassa.controller.admin.member.dto.MemberUpdateRequestDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.MemberService;
import ee.revkassa.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private static final int MAX_PAGE = 10_000;

    private final MemberService memberService;
    private final PurchaseService purchaseService;

    @GetMapping
    @Operation(summary = "All members, active and inactive, by name",
            description = "Admin password session only. passwordSet = false: the member has not used the invite link yet.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<AdminMemberDto> getMembers() {
        return memberService.getMembers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a member: emails an invite link to set a password and returns the new PIN once",
            description = "The member is active and gets the first active membership level. Show the PIN to the admin "
                    + "now: it cannot be read again (only reset). If the email cannot be sent, nothing is saved (503). "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created, invite sent"),
            @ApiResponse(responseCode = "400", description = "Invalid fields, e.g. email missing or malformed (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Email already used by another member (133), "
                    + "not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Mail server failed, nothing was saved; try again (123)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MemberCreatedDto createMember(@RequestBody @Valid MemberCreateRequestDto memberCreateRequest,
                                         @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return memberService.createMember(memberCreateRequest, principal.memberId());
    }

    @PutMapping("/{memberId}")
    @Operation(summary = "Change a member: name, email, role, active, fee exemption",
            description = "Send back the version you loaded; if someone saved in between, 409 and nothing changes. "
                    + "A deactivated member is logged out at their next request; a member who loses the admin role "
                    + "loses their admin session. A new email closes the links sent to the old one. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated, with the new version"),
            @ApiResponse(responseCode = "400", description = "Invalid fields or malformed id (errorCode 103 / 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Email already used by another member (133), "
                    + "this would leave no active admin (134), not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such member (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Someone else changed the member meanwhile; reload (105)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AdminMemberDto updateMember(@PathVariable Long memberId,
                                       @RequestBody @Valid MemberUpdateRequestDto memberUpdateRequest,
                                       @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return memberService.updateMember(memberId, memberUpdateRequest, principal.memberId());
    }

    @PostMapping("/{memberId}/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Email the member a new invite link (valid 7 days) to set a password",
            description = "Earlier open links of this member stop working. Admin password session, X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Invite sent"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member not active (112), not an admin password session "
                    + "or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such member (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Mail server failed, nothing was saved; try again (123)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void sendInvite(@PathVariable Long memberId,
                           @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        memberService.sendInvite(memberId, principal.memberId());
    }

    @PostMapping("/{memberId}/pin")
    @Operation(summary = "Give the member a new random PIN and return it once",
            description = "The old PIN stops working at once. Show the PIN to the admin now: it cannot be read again. "
                    + "The member's version changes (reload the form). Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK, the new PIN"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such member (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MemberPinDto resetPin(@PathVariable Long memberId,
                                 @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return memberService.resetPin(memberId, principal.memberId());
    }

    @PostMapping("/{memberId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Correct the member's balance by hand",
            description = "amountCents: positive raises the balance, negative lowers it, not 0, at most 100 000 € either "
                    + "way. The note is required and the member sees it in their history (\"Parandus · <note>\"). "
                    + "Works for inactive members too. Returns the member with the new balance. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Ledger row added"),
            @ApiResponse(responseCode = "400", description = "Amount 0 or too large, note missing (errorCode 103), "
                    + "malformed id (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such member (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AdminMemberDto addAdjustment(@PathVariable Long memberId,
                                        @RequestBody @Valid AdjustmentCreateRequestDto adjustmentCreateRequest,
                                        @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return memberService.addAdjustment(memberId, adjustmentCreateRequest, principal.memberId());
    }

    @GetMapping("/{memberId}/purchases")
    @Operation(summary = "The member's purchases, newest first, 20 per page, undone ones included",
            description = "Admin password session only. Undo one with POST /api/purchases/{id}/undo "
                    + "(an admin may undo any purchase at any time). hasMore = true: ask for page + 1.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK, purchases of this page (empty past the last page)"),
            @ApiResponse(responseCode = "400", description = "page or id is not a number (104), page not 0-10000 (103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such member (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MemberPurchasePageDto getMemberPurchases(@PathVariable Long memberId,
                                                    @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return purchaseService.getMemberPurchases(memberId, page);
    }
}
