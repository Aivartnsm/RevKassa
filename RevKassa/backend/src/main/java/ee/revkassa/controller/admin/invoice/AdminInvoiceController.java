package ee.revkassa.controller.admin.invoice;

import ee.revkassa.controller.admin.invoice.dto.InvoiceCandidateDto;
import ee.revkassa.controller.admin.invoice.dto.InvoiceCreateRequestDto;
import ee.revkassa.controller.admin.invoice.dto.InvoiceDto;
import ee.revkassa.controller.admin.invoice.dto.InvoicePageDto;
import ee.revkassa.controller.admin.invoice.dto.InvoicePreviewDto;
import ee.revkassa.controller.admin.invoice.dto.InvoiceSendResultDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
public class AdminInvoiceController {

    private static final int MAX_PAGE = 10_000;

    private final InvoiceService invoiceService;

    @GetMapping("/candidates")
    @Operation(summary = "Members in debt to invoice, largest debt first",
            description = "Also deactivated members (active = false): their debt is still owed. lastInvoiceAt and "
                    + "lastInvoiceStatus describe the newest invoice. defaultSelected is false when an invoice reached "
                    + "the member in the last 7 days. Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK (empty list when nobody is in debt)"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<InvoiceCandidateDto> getCandidates() {
        return invoiceService.getCandidates();
    }

    @GetMapping("/preview")
    @Operation(summary = "The invoice email a member would get now: recipient, subject and body",
            description = "Nothing is saved or sent. The number is the next free one and is not reserved. "
                    + "Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "memberId missing or malformed (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member has no debt (146), not an admin password "
                    + "session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such member (101), payee name or IBAN not set (131)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public InvoicePreviewDto getPreview(@RequestParam Long memberId) {
        return invoiceService.getPreview(memberId);
    }

    @PostMapping
    @Operation(summary = "Create and email an invoice to each listed member",
            description = "Each member separately: SENT, FAILED (saved with the mail server's error, can be resent) or "
                    + "SKIPPED with skipReason NOT_IN_DEBT, MEMBER_NOT_FOUND or ERROR (nothing saved). One bad address "
                    + "does not stop the others. Balances never change. Ids listed twice are invoiced once. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "One result per member, in the order given"),
            @ApiResponse(responseCode = "400", description = "memberIds missing, empty, longer than 200 or with an "
                    + "empty id (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Payee name or IBAN not set (131): nothing was sent",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<InvoiceSendResultDto> sendInvoices(@RequestBody @Valid InvoiceCreateRequestDto invoiceCreateRequest,
                                                   @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return invoiceService.sendInvoices(invoiceCreateRequest, principal.memberId());
    }

    @GetMapping
    @Operation(summary = "Invoices, newest first, 20 per page",
            description = "Only one member's when memberId is given. Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "page below 0 or above 10 000 (errorCode 103), "
                    + "malformed memberId or page (104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public InvoicePageDto getInvoices(@RequestParam(required = false) Long memberId,
                                      @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page) {
        return invoiceService.getInvoices(memberId, page);
    }

    @PostMapping("/{invoiceId}/resend")
    @Operation(summary = "Send a FAILED invoice again, with the same number, amount and due date",
            description = "Goes to the member's current email, so correct the address first. Answers with the invoice "
                    + "also when sending fails again: then it is still FAILED with the new error. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tried: status SENT, or FAILED with the error"),
            @ApiResponse(responseCode = "400", description = "Malformed id (errorCode 104)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Already sent (147), member has no debt any more (146), "
                    + "not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such invoice (101), payee name or IBAN not set (131)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public InvoiceDto resendInvoice(@PathVariable Long invoiceId,
                                    @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return invoiceService.resendInvoice(invoiceId, principal.memberId());
    }
}
