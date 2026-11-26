package ee.revkassa.controller.admin.report;

import ee.revkassa.controller.admin.report.dto.SalesReportDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.service.SalesReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final SalesReportService salesReportService;

    @GetMapping("/sales")
    @Operation(summary = "Sales report: units, revenue, purchase cost and margin per product between two days",
            description = "from and to as YYYY-MM-DD, both days included, club time (a purchase at 00:30 belongs to "
                    + "the new day). No from = from the first sale; no to = today. Undone purchases do not count. "
                    + "Sold products come most sold first, then active products that sold nothing. Revenue uses the "
                    + "price of the moment of sale; cost the purchase price stored with the sale, or the product's "
                    + "current one for older sales; cost and margin are null where some units have no purchase price. "
                    + "Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "from after to (errorCode 142), malformed date (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SalesReportDto getSalesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return salesReportService.getSalesReport(from, to);
    }
}
