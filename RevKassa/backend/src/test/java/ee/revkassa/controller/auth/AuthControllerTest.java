package ee.revkassa.controller.auth;

import ee.revkassa.controller.auth.dto.DeviceStatusDto;
import ee.revkassa.controller.auth.dto.LoginRequestDto;
import ee.revkassa.controller.common.dto.MemberSessionDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.security.LoginMethodEnum;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP mapping only: validation, status codes and ApiError bodies. Security filters are off here;
 * sessions, CSRF and access rules are tested against the real filter chain in LoginFlowTest.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
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

    @Test
    void loginWithPin_validPin_returnsMemberSession() throws Exception {
        when(authService.loginWithPin(eq("012345"), eq("tablet-token"), any(), any()))
                .thenReturn(memberSession(LoginMethodEnum.PIN));

        mockMvc.perform(post("/api/auth/pin").cookie(new Cookie("REVKASSA_DEVICE", "tablet-token"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"012345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(2))
                .andExpect(jsonPath("$.displayName").value("Kaspar Kask"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.balanceCents").value(-2740))
                .andExpect(jsonPath("$.loginMethod").value("PIN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"pin\":\"12345\"}", "{\"pin\":\"1234567\"}", "{\"pin\":\"12a456\"}",
            "{\"pin\":\" 12345\"}", "{\"pin\":null}", "{}"})
    void loginWithPin_pinNotSixDigits_returns400AndChecksNothing(String body) throws Exception {
        mockMvc.perform(post("/api/auth/pin").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(Error.INVALID_DATA_IN_FIELDS.getErrorCode()))
                .andExpect(jsonPath("$.fieldErrors.pin").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void loginWithPin_untrustedDevice_returns403WithErrorCode() throws Exception {
        when(authService.loginWithPin(any(), isNull(), any(), any()))
                .thenThrow(new ForbiddenException(Error.DEVICE_NOT_TRUSTED));

        mockMvc.perform(post("/api/auth/pin").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"111111\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(116))
                .andExpect(jsonPath("$.message").value("PIN login is only allowed on a registered device."));
    }

    @Test
    void loginWithPassword_validInput_returnsMemberSession() throws Exception {
        when(authService.loginWithPassword(any(LoginRequestDto.class), any(), any()))
                .thenReturn(memberSession(LoginMethodEnum.PASSWORD));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kaspar@example.org\",\"password\":\"parool123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginMethod").value("PASSWORD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"password\":\"parool123\"}", "{\"email\":\"\",\"password\":\"parool123\"}",
            "{\"email\":\"not-an-email\",\"password\":\"parool123\"}", "{\"email\":\"kaspar@example.org\"}",
            "{\"email\":\"kaspar@example.org\",\"password\":\"  \"}"})
    void loginWithPassword_missingOrMalformedInput_returns400(String body) throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(Error.INVALID_DATA_IN_FIELDS.getErrorCode()));

        verifyNoInteractions(authService);
    }

    @Test
    void loginWithPassword_wrongCredentials_returns403WithoutDetails() throws Exception {
        when(authService.loginWithPassword(any(LoginRequestDto.class), any(), any()))
                .thenThrow(new ForbiddenException(Error.WRONG_EMAIL_OR_PASSWORD));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kaspar@example.org\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(114))
                .andExpect(jsonPath("$.message").value("Wrong email or password."));
    }

    @Test
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    private static MemberSessionDto memberSession(LoginMethodEnum loginMethod) {
        MemberSessionDto dto = new MemberSessionDto();
        dto.setMemberId(2L);
        dto.setDisplayName("Kaspar Kask");
        dto.setRole(MemberRoleEnum.MEMBER);
        dto.setBalanceCents(-2740L);
        dto.setLoginMethod(loginMethod);
        return dto;
    }
}
