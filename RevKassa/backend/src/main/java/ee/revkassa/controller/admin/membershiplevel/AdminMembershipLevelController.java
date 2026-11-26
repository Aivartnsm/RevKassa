package ee.revkassa.controller.admin.membershiplevel;

import ee.revkassa.controller.admin.membershiplevel.dto.MembershipLevelCreateRequestDto;
import ee.revkassa.controller.admin.membershiplevel.dto.MembershipLevelDto;
import ee.revkassa.controller.admin.membershiplevel.dto.MembershipLevelUpdateRequestDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.MembershipLevelService;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/levels")
@RequiredArgsConstructor
public class AdminMembershipLevelController {

    private final MembershipLevelService membershipLevelService;

    @GetMapping
    @Operation(summary = "All membership levels, active and inactive, in display order",
            description = "memberCount = active members on the level. deletable = nothing refers to the level; "
                    + "otherwise it can only be deactivated. Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<MembershipLevelDto> getLevels() {
        return membershipLevelService.getLevels();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a membership level (active, last in order, no monthly fee until one is set)",
            description = "Name unique regardless of case. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Name missing or longer than 50 (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Name already used (135), not an admin password session "
                    + "or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MembershipLevelDto createLevel(@RequestBody @Valid MembershipLevelCreateRequestDto levelCreateRequest,
                                          @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return membershipLevelService.createLevel(levelCreateRequest, principal.memberId());
    }

    @PutMapping("/{levelId}")
    @Operation(summary = "Rename, reorder or (de)activate a membership level",
            description = "A deactivated level is not offered for new choices; its members keep it. Renaming also "
                    + "changes the name shown with past fees. The last active level cannot be deactivated. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Invalid fields or malformed id (errorCode 103 / 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Name already used (135), last active level (137), "
                    + "not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such level (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MembershipLevelDto updateLevel(@PathVariable Long levelId,
                                          @RequestBody @Valid MembershipLevelUpdateRequestDto levelUpdateRequest,
                                          @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return membershipLevelService.updateLevel(levelId, levelUpdateRequest, principal.memberId());
    }

    @DeleteMapping("/{levelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a membership level nothing refers to",
            description = "Only when no member (also inactive), fee, charge or event refers to it; otherwise deactivate "
                    + "it. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Level in use (136), last active level (137), "
                    + "not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such level (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void deleteLevel(@PathVariable Long levelId,
                            @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        membershipLevelService.deleteLevel(levelId, principal.memberId());
    }
}
