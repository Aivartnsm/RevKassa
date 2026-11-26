package ee.revkassa.controller.admin.dashboard;

import ee.revkassa.controller.admin.dashboard.dto.DashboardDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "The admin overview: open debts, payments to confirm, stock to check, largest debts and "
            + "this month's best sellers and upcoming events",
            description = "Debts are positive owed amounts and include inactive members (active = false). Stock "
                    + "figures count products for sale only: LOW = 0 up to the warning level, NEGATIVE = below 0. "
                    + "largestDebts and topProducts hold at most 5 rows; undone purchases are not counted. "
                    + "upcomingEvents: at most 3 PLANNED events from now on, soonest first, with active participants. "
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
    public DashboardDto getDashboard() {
        return dashboardService.getDashboard();
    }
}
