package ee.revkassa.controller.me;

import ee.revkassa.controller.payment.dto.PaymentDto;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Named apart from PaymentController: both would otherwise be the bean "paymentController"
@RestController
@RequestMapping("/api/me/payments")
@RequiredArgsConstructor
public class MemberPaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Your payments with one status, newest first, at most 50",
            description = "Any logged-in member (PIN or password), only their own payments. status: PENDING (default), "
                    + "CONFIRMED or REJECTED; a rejected payment carries the admin's decisionNote.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK, possibly an empty list"),
            @ApiResponse(responseCode = "400", description = "Unknown status (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member deactivated (112)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<PaymentDto> getMyPayments(@RequestParam(defaultValue = "PENDING") PaymentStatusEnum status,
                                          @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return paymentService.getMemberPayments(principal.memberId(), status);
    }
}
