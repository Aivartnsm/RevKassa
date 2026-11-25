package ee.revkassa.controller.payment;

import ee.revkassa.controller.payment.dto.PaymentCreateRequestDto;
import ee.revkassa.controller.payment.dto.PaymentDetailsDto;
import ee.revkassa.controller.payment.dto.PaymentDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/details")
    @Operation(summary = "Where to transfer money: recipient name, IBAN and a suggested description",
            description = "Any logged-in member (PIN or password). The admin sets the recipient and IBAN "
                    + "(/api/admin/organisation); the description carries the member's name so the admin recognises the "
                    + "transfer on the bank statement.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member deactivated (112)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "An admin has not set the recipient and IBAN yet (131)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PaymentDetailsDto getPaymentDetails(@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return paymentService.getPaymentDetails(principal.memberId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a bank transfer you made: amount and an optional reference",
            description = "Any logged-in member (PIN or password), always for themselves. The payment is PENDING and the "
                    + "balance does not change until an admin confirms it. No upper limit on the amount. Reporting the "
                    + "same transfer twice makes two payments; the admin rejects the extra one. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment reported, status PENDING"),
            @ApiResponse(responseCode = "400", description = "amountCents missing or not above 0, reference over 140 "
                    + "characters (103) or the body cannot be read (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member deactivated (112) or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PaymentDto reportPayment(@RequestBody @Valid PaymentCreateRequestDto paymentCreateRequest,
                                    @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return paymentService.reportPayment(principal.memberId(), paymentCreateRequest);
    }
}
