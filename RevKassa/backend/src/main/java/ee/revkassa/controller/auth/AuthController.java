package ee.revkassa.controller.auth;

import ee.revkassa.controller.auth.dto.DeviceStatusDto;
import ee.revkassa.controller.auth.dto.LoginRequestDto;
import ee.revkassa.controller.auth.dto.PasswordResetRequestDto;
import ee.revkassa.controller.auth.dto.PinLoginRequestDto;
import ee.revkassa.controller.auth.dto.SetPasswordRequestDto;
import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/device")
    @Operation(summary = "Is this device a trusted device (show PIN keypad) or not (show email + password form)",
            description = "Reads the HttpOnly REVKASSA_DEVICE cookie. Unknown or revoked devices are reported as not trusted.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DeviceStatusDto getDeviceStatus(
            @Parameter(hidden = true) @CookieValue(name = DeviceService.DEVICE_COOKIE_NAME, required = false) String deviceToken) {
        return authService.getDeviceStatus(deviceToken);
    }

    @PostMapping("/pin")
    @Operation(summary = "Log in with a 6-digit PIN (trusted device only), member session expires after 1 min idle",
            description = "Needs the REVKASSA_DEVICE cookie of a registered, not revoked device and the X-XSRF-TOKEN header. "
                    + "Never gives admin rights.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logged in, new session cookie set"),
            @ApiResponse(responseCode = "400", description = "PIN is not exactly 6 digits (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Device not trusted (116), wrong PIN (111, with remainingAttempts), "
                    + "member not active (112) or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Too many wrong PINs on this device (113): Retry-After header "
                    + "gives the seconds left, nextLockoutSeconds how long the next wrong PIN will lock",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MemberSessionDto loginWithPin(
            @RequestBody @Valid PinLoginRequestDto pinLoginRequest,
            @Parameter(hidden = true) @CookieValue(name = DeviceService.DEVICE_COOKIE_NAME, required = false) String deviceToken,
            HttpServletRequest request, HttpServletResponse response) {
        return authService.loginWithPin(pinLoginRequest.getPin(), deviceToken, request, response);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password (any device), session expires after 30 min idle",
            description = "ADMIN members also get admin rights. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logged in, new session cookie set"),
            @ApiResponse(responseCode = "400", description = "Email or password missing, or email malformed (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Wrong email or password, or password not set (114, with "
                    + "remainingAttempts), member not active (112) or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Too many failed logins from this IP (117): Retry-After header "
                    + "gives the seconds left, nextLockoutSeconds how long the next failure will lock",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MemberSessionDto loginWithPassword(@RequestBody @Valid LoginRequestDto loginRequest,
                                              HttpServletRequest request, HttpServletResponse response) {
        return authService.loginWithPassword(loginRequest, request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End the session (PIN or password); the trusted device cookie stays",
            description = "Idempotent: also 204 without a session. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Logged out, session cookie cleared"),
            @ApiResponse(responseCode = "403", description = "CSRF token missing (errorCode 118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "\"Forgot password?\": email a one-time link (valid 1 hour) to an active member",
            description = "Always 204, whether or not the email belongs to a member, so it cannot be used to find members. "
                    + "At most 5 requests per hour per IP and 5 emails per hour per address. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Request received"),
            @ApiResponse(responseCode = "400", description = "Email missing or malformed (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "CSRF token missing (errorCode 118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests from this IP (122), Retry-After header "
                    + "gives the seconds to wait",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void requestPasswordReset(@RequestBody @Valid PasswordResetRequestDto passwordResetRequest,
                                     HttpServletRequest request) {
        authService.requestPasswordReset(passwordResetRequest.getEmail(), request);
    }

    @PostMapping("/set-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a password with the token from an invite or reset link",
            description = "The link works once; all other open links of the member stop working too. Does not log in. "
                    + "Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Password set"),
            @ApiResponse(responseCode = "400", description = "Password shorter than 10 characters or longer than 72 bytes, "
                    + "or token missing (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Link unknown, used, expired or member not active (121), "
                    + "or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void setPassword(@RequestBody @Valid SetPasswordRequestDto setPasswordRequest) {
        authService.setPassword(setPasswordRequest.getToken(), setPasswordRequest.getPassword());
    }
}
