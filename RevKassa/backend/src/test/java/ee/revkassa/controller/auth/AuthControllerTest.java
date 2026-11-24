package ee.revkassa.controller.auth;

import ee.revkassa.controller.auth.dto.DeviceStatusDto;
import ee.revkassa.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void getDeviceStatus_withDeviceCookie_returnsTrustedDevice() throws Exception {
        when(authService.getDeviceStatus("tablet-token")).thenReturn(new DeviceStatusDto(true, "Baaritahvel"));

        mockMvc.perform(get("/api/auth/device").cookie(new Cookie("REVKASSA_DEVICE", "tablet-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trusted").value(true))
                .andExpect(jsonPath("$.name").value("Baaritahvel"));
    }

    @Test
    void getDeviceStatus_withoutDeviceCookie_returnsNotTrusted() throws Exception {
        when(authService.getDeviceStatus(isNull())).thenReturn(new DeviceStatusDto(false, null));

        mockMvc.perform(get("/api/auth/device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trusted").value(false))
                .andExpect(jsonPath("$.name").doesNotExist());
    }
}
