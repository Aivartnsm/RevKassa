package ee.revkassa.controller.admin.payment;

import ee.revkassa.controller.admin.payment.dto.AdminPaymentCreateRequestDto;
import ee.revkassa.controller.admin.payment.dto.AdminPaymentDto;
import ee.revkassa.controller.admin.payment.dto.PaymentRejectRequestDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.persistence.payment.PaymentStatusEnum;
import ee.revkassa.service.PaymentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Payments with one status, at most 100",
            description = "Admin with a password session. PENDING (default) is the queue to work through, oldest first; "
                    + "CONFIRMED and REJECTED come latest decision first.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK, possibly an empty list"),
            @ApiResponse(responseCode = "400", description = "Unknown status (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin, or an admin with a PIN session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<AdminPaymentDto> getPayments(@RequestParam(defaultValue = "PENDING") PaymentStatusEnum status) {
        return paymentService.getPayments(status);
    }

    @PostMapping("/{paymentId}/confirm")
    @Operation(summary = "Confirm a pending payment: its amount goes to the member's balance",
            description = "Admin with a password session, after seeing the money in the bank. Writes exactly one PAYMENT "
                    + "row to the ledger. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Confirmed"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Already confirmed or rejected (132), not an admin or "
                    + "CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such payment (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AdminPaymentDto confirmPayment(@PathVariable Long paymentId,
                                          @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return paymentService.confirmPayment(paymentId, principal.memberId());
    }

    @PostMapping("/{paymentId}/reject")
    @Operation(summary = "Reject a pending payment, with an optional note the member sees",
            description = "Admin with a password session, e.g. when the money never arrived or the payment was reported "
                    + "twice. The balance does not change. The body may be left out. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rejected"),
            @ApiResponse(responseCode = "400", description = "Note over 500 characters (103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Already confirmed or rejected (132), not an admin or "
                    + "CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such payment (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AdminPaymentDto rejectPayment(@PathVariable Long paymentId,
                                         @RequestBody(required = false) @Valid PaymentRejectRequestDto paymentRejectRequest,
                                         @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return paymentService.rejectPayment(paymentId, paymentRejectRequest, principal.memberId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enter a payment for a member, confirmed at once",
            description = "Admin with a password session, for money seen on the bank statement that nobody reported. "
                    + "Saved as CONFIRMED with one PAYMENT ledger row. Works for deactivated members too (settling a "
                    + "debt). Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment entered and confirmed"),
            @ApiResponse(responseCode = "400", description = "memberId or amountCents missing or not above 0, reference "
                    + "over 140 characters (103) or the body cannot be read (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such member (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AdminPaymentDto createPayment(@RequestBody @Valid AdminPaymentCreateRequestDto paymentCreateRequest,
                                         @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return paymentService.createConfirmedPayment(paymentCreateRequest, principal.memberId());
    }
}
