package ee.revkassa.controller.admin.organisation;

import ee.revkassa.controller.admin.organisation.dto.OrganisationDto;
import ee.revkassa.controller.admin.organisation.dto.OrganisationUpdateRequestDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.OrganisationService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organisation")
@RequiredArgsConstructor
public class AdminOrganisationController {

    private final OrganisationService organisationService;

    @GetMapping
    @Operation(summary = "Payee name and IBAN for members' bank transfers and invoices",
            description = "Admin with a password session. name and iban are null until an admin sets them.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK, with the version to send back when saving"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public OrganisationDto getOrganisation() {
        return organisationService.getOrganisation();
    }

    @PutMapping
    @Operation(summary = "Change the payee name and IBAN",
            description = "Send back the version you loaded; if someone saved in between, 409 and nothing changes. The "
                    + "IBAN may contain spaces and lower case; it is saved without spaces, in capitals. When the IBAN "
                    + "changes, every active admin gets an email about it. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, with the new version"),
            @ApiResponse(responseCode = "400", description = "name missing or over 100 characters, IBAN missing or "
                    + "invalid, version missing (103, fieldErrors)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Someone else changed the details meanwhile; reload (105)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public OrganisationDto updateOrganisation(@RequestBody @Valid OrganisationUpdateRequestDto organisationUpdateRequest,
                                              @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return organisationService.updateOrganisation(organisationUpdateRequest, principal.memberId());
    }
}
