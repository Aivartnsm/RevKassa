package ee.revkassa.controller.purchase;

import ee.revkassa.controller.purchase.dto.PurchaseCreateRequestDto;
import ee.revkassa.controller.purchase.dto.PurchaseDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping
    @Operation(summary = "Buy for your own balance (guests too): lines of productId and quantity",
            description = "Any logged-in member (PIN or password). Prices always come from the server; expectedTotalCents "
                    + "is the total the member saw and only compared: if it differs, 409 and nothing is charged. "
                    + "requestId is made once per basket by the client: sending it again (retry after a lost "
                    + "connection, double tap) returns the purchase already recorded with 200 instead of charging twice. "
                    + "The same product in two lines is merged. No balance limit. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Purchase recorded, with the new balance and undoableUntil"),
            @ApiResponse(responseCode = "200", description = "This requestId was already recorded: the earlier purchase"),
            @ApiResponse(responseCode = "400", description = "requestId or expectedTotalCents missing, no lines, more than 50 "
                    + "lines, quantity not 1-99 (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "A product is hidden (126), requestId belongs to another member "
                    + "(104), member deactivated (112) or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "A product does not exist (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Prices changed since the member saw them (127): reload products, "
                    + "show the new total and send again after the member confirms",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PurchaseDto> createPurchase(@RequestBody @Valid PurchaseCreateRequestDto purchaseCreateRequest,
                                                      @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        PurchaseService.PurchaseResult result = purchaseService.createPurchase(principal.memberId(), purchaseCreateRequest);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.purchase());
    }

    @PostMapping("/{purchaseId}/undo")
    @Operation(summary = "Undo a purchase: the total goes back to the balance and the products back to stock",
            description = "The member may undo their own purchase until undoableUntil (10 minutes); an admin with a password "
                    + "session may undo any purchase at any time (a PIN session never has admin rights). Nothing is deleted: "
                    + "the purchase becomes REVERSED and reversal rows are added. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Purchase undone: status REVERSED, with the new balance"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Already undone (128), the 10 minutes have passed (129), "
                    + "member deactivated (112) or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such purchase, or it belongs to another member (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PurchaseDto undoPurchase(@PathVariable Long purchaseId,
                                    @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return purchaseService.undoPurchase(purchaseId, principal.memberId(), principal.loginMethod());
    }
}
