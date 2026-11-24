package ee.revkassa.controller.admin.device;

import ee.revkassa.controller.admin.device.dto.DeviceCreateRequestDto;
import ee.revkassa.controller.admin.device.dto.DeviceDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.TrustedDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/devices")
@RequiredArgsConstructor
public class AdminDeviceController {

    private final TrustedDeviceService trustedDeviceService;

    @GetMapping
    @Operation(summary = "All trusted devices, active first",
            description = "Name, registered at/by, last PIN login, revoked at/by. Admin password session only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Not an admin password session (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<DeviceDto> findAllDevices() {
        return trustedDeviceService.findAllDevices();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register THIS browser as a trusted device (PIN login becomes possible here)",
            description = "Sets the HttpOnly REVKASSA_DEVICE cookie on the calling browser. The token is never in the "
                    + "response body; the database stores only its SHA-256 hash. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Registered, device cookie set"),
            @ApiResponse(responseCode = "400", description = "Name missing or longer than 100 characters (errorCode 103)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "This browser is already a trusted device (120), "
                    + "not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DeviceDto registerThisDevice(
            @RequestBody @Valid DeviceCreateRequestDto deviceCreateRequest,
            @Parameter(hidden = true) @CookieValue(name = DeviceService.DEVICE_COOKIE_NAME, required = false) String deviceToken,
            @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal,
            HttpServletResponse response) {
        return trustedDeviceService.registerThisDevice(deviceCreateRequest.getName(), deviceToken, principal.memberId(), response);
    }

    @PostMapping("/{deviceId}/revoke")
    @Operation(summary = "Revoke a trusted device: PIN login stops working there at once",
            description = "The row is kept for history. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Revoked"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Device already revoked (119), "
                    + "not an admin password session or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No such device (101)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DeviceDto revokeDevice(@PathVariable Long deviceId,
                                  @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return trustedDeviceService.revokeDevice(deviceId, principal.memberId());
    }
}
