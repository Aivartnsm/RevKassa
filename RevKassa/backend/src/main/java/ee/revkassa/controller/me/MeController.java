package ee.revkassa.controller.me;

import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.controller.me.dto.ChangePasswordRequestDto;
import ee.revkassa.controller.me.dto.ChangePinRequestDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.MeService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @GetMapping
    @Operation(summary = "Logged-in member's name, role, current balance and login method",
            description = "Reads the member from the database on every call, so the balance is always fresh.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member was deactivated, session ended (errorCode 112)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MemberSessionDto getMe(@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return meService.getMe(principal);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the logged-in member's own password",
            description = "The current password must match. Wrong current passwords are limited per member like logins. "
                    + "Other sessions stay valid. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "Current password missing, new password shorter than 10 "
                    + "characters or longer than 72 bytes (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Current password wrong (124, with remainingAttempts), "
                    + "member deactivated (112) or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Too many wrong current passwords (117): Retry-After header "
                    + "gives the seconds left, nextLockoutSeconds how long the next failure will lock",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void changePassword(@RequestBody @Valid ChangePasswordRequestDto changePasswordRequest,
                               @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        meService.changePassword(principal, changePasswordRequest.getCurrentPassword(), changePasswordRequest.getNewPassword());
    }

    @PutMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the logged-in member's own PIN",
            description = "The current PIN must match. The new PIN must be 6 digits, differ from the current one and not "
                    + "be used by anyone. Wrong current PINs and unavailable new PINs are limited per member like logins. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "PIN changed: the old PIN stops working at once"),
            @ApiResponse(responseCode = "400", description = "Current or new PIN missing or not exactly 6 digits (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Current PIN wrong (143, with remainingAttempts), new PIN not "
                    + "available (144, with remainingAttempts), new PIN same as current (145), member deactivated (112) "
                    + "or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts (113): Retry-After header gives the "
                    + "seconds left, nextLockoutSeconds how long the next failure will lock",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void changePin(@RequestBody @Valid ChangePinRequestDto changePinRequest,
                          @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        meService.changePin(principal, changePinRequest.getCurrentPin(), changePinRequest.getNewPin());
    }
}
