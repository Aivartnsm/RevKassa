package ee.revkassa.controller.me;

import ee.revkassa.controller.me.dto.AccountEntryPageDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.AccountEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/entries")
@RequiredArgsConstructor
public class AccountEntryController {

    private static final int MAX_PAGE = 10_000;

    private final AccountEntryService accountEntryService;

    @GetMapping
    @Operation(summary = "Your balance history, newest first, 20 rows per page",
            description = "Any logged-in member (PIN or password), only their own rows. Each row has the signed amount "
                    + "and a description to show as is (e.g. \"2 × Saku Originaal\"). hasMore = true: ask for page + 1.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK, entries of this page (empty past the last page)"),
            @ApiResponse(responseCode = "400", description = "page is not a number (104) or not 0-10000 (103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member deactivated (112)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AccountEntryPageDto getMyEntries(@RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
                                            @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return accountEntryService.getMemberEntries(principal.memberId(), page);
    }
}
