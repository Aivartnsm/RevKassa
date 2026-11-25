package ee.revkassa.controller.admin.stock;

import ee.revkassa.controller.admin.stock.dto.DeliveryCreateRequestDto;
import ee.revkassa.controller.admin.stock.dto.ProductStockDto;
import ee.revkassa.controller.admin.stock.dto.StocktakeCreateRequestDto;
import ee.revkassa.controller.admin.stock.dto.StocktakeResultDto;
import ee.revkassa.controller.admin.stock.dto.WriteOffCreateRequestDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.StockService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockService stockService;

    @PostMapping("/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a delivery: one stock movement per line, stock goes up",
            description = "Lines: productId, quantity > 0, optional unit cost in cents. All lines are saved or none "
                    + "(an unknown product cancels the delivery). Returns each product's new stock. "
                    + "Hidden products can be restocked too. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Recorded, with the new stock per product"),
            @ApiResponse(responseCode = "400", description = "No lines, quantity not > 0, negative cost, more than 100 lines "
                    + "(errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "A product does not exist; nothing was saved (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<ProductStockDto> recordDelivery(@RequestBody @Valid DeliveryCreateRequestDto deliveryCreateRequest,
                                                @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return stockService.recordDelivery(deliveryCreateRequest, principal.memberId());
    }

    @PostMapping("/stocktake")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a stock count: the counted quantity becomes the stock",
            description = "Lines: productId and countedQty (>= 0); leave out products that were not counted. For each "
                    + "product the server writes one STOCKTAKE movement of (counted - current stock), nothing when they "
                    + "match. The difference is computed at save time, so sales after the page loaded are taken into "
                    + "account. All lines or none. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Recorded: stock before, count and correction per product"),
            @ApiResponse(responseCode = "400", description = "No lines, count missing or negative, more than 200 lines "
                    + "(errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "A product is counted twice (125), not an admin password "
                    + "session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "A product does not exist; nothing was saved (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<StocktakeResultDto> recordStocktake(@RequestBody @Valid StocktakeCreateRequestDto stocktakeCreateRequest,
                                                    @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return stockService.recordStocktake(stocktakeCreateRequest, principal.memberId());
    }

    @PostMapping("/write-offs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Write off broken or expired units: stock goes down",
            description = "Lines: productId and quantity (> 0, the number of units removed); a note is required. "
                    + "All lines or none. Returns each product's new stock. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Recorded, with the new stock per product"),
            @ApiResponse(responseCode = "400", description = "No lines, quantity not > 0 or note missing (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "A product does not exist; nothing was saved (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<ProductStockDto> recordWriteOff(@RequestBody @Valid WriteOffCreateRequestDto writeOffCreateRequest,
                                                @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return stockService.recordWriteOff(writeOffCreateRequest, principal.memberId());
    }
}
