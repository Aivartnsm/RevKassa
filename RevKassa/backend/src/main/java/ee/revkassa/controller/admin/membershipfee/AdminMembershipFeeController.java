package ee.revkassa.controller.admin.membershipfee;

import ee.revkassa.controller.admin.membershipfee.dto.ChargeWaiveRequestDto;
import ee.revkassa.controller.admin.membershipfee.dto.LevelFeesDto;
import ee.revkassa.controller.admin.membershipfee.dto.MembershipChargeDto;
import ee.revkassa.controller.admin.membershipfee.dto.MembershipFeesCreateRequestDto;
import ee.revkassa.controller.admin.membershipfee.dto.MonthChargesDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.MembershipChargeService;
import ee.revkassa.service.MembershipFeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/admin/fees")
@RequiredArgsConstructor
public class AdminMembershipFeeController {

    private final MembershipFeeService membershipFeeService;
    private final MembershipChargeService membershipChargeService;

    @GetMapping
    @Operation(summary = "Monthly fee per membership level: current amount, next scheduled change and history",
            description = "All levels in display order. currentAmountCents is this month's fee (0 if never set); "
                    + "upcoming is the next scheduled change or null; history is newest month first, scheduled "
                    + "rows included. Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<LevelFeesDto> getFees() {
        return membershipFeeService.getFees();
    }

    @PostMapping
    @Operation(summary = "Set new monthly fees from a month on, for some or all levels",
            description = "validFrom as \"YYYY-MM\": the current month or later. Months already charged keep their "
                    + "amounts, so members who have not paid owe the old fees plus the new ones. Saving a month again "
                    + "replaces it while that month is not charged for the level. All listed levels are saved or none. "
                    + "Returns the updated view. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, with all levels' fees"),
            @ApiResponse(responseCode = "400", description = "Invalid fields: amount < 0 or > 10 000 €, list empty, "
                    + "a level listed twice (103); malformed month (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Month in the past (139), month already charged for a "
                    + "level (140), not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such level (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<LevelFeesDto> saveFees(@RequestBody @Valid MembershipFeesCreateRequestDto feesCreateRequest,
                                       @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return membershipFeeService.saveFees(feesCreateRequest, principal.memberId());
    }

    @GetMapping("/charges")
    @Operation(summary = "A month's fees by member name, with charged and waived totals",
            description = "month as \"YYYY-MM\", the current month if left out. Each fee keeps the level and amount it "
                    + "was charged with. Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK (empty list for a month nobody was charged for)"),
            @ApiResponse(responseCode = "400", description = "Malformed month (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MonthChargesDto getMonthCharges(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return membershipChargeService.getMonthCharges(month);
    }

    @PostMapping("/charges/{chargeId}/waive")
    @Operation(summary = "Waive (cancel) a charged monthly fee: the whole amount goes back to the member",
            description = "The note is required and the member sees it in their history. Any month, also for an "
                    + "inactive member; the member is never charged for that month again. Final: to undo, use a "
                    + "balance adjustment. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Waived"),
            @ApiResponse(responseCode = "400", description = "Note missing or longer than 500 (errorCode 103), "
                    + "malformed id (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Already waived (141), not an admin password session or "
                    + "CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such charge (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MembershipChargeDto waiveCharge(@PathVariable Long chargeId,
                                           @RequestBody @Valid ChargeWaiveRequestDto chargeWaiveRequest,
                                           @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return membershipChargeService.waiveCharge(chargeId, chargeWaiveRequest, principal.memberId());
    }
}
