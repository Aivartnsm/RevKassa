package ee.revkassa.controller.auth;

import ee.revkassa.controller.auth.dto.DeviceStatusDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
            @CookieValue(name = DeviceService.DEVICE_COOKIE_NAME, required = false) String deviceToken) {
        return authService.getDeviceStatus(deviceToken);
    }
}
