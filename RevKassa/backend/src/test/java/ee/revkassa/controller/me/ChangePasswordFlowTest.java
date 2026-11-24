package ee.revkassa.controller.me;

import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B37: PUT /api/me/password through the real security chain and PostgreSQL. Every test has its own member and IP,
 * because lockouts live in memory for the whole test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestData.class)
@ExtendWith(OutputCaptureExtension.class)
class ChangePasswordFlowTest {

    private static final String OLD_PASSWORD = "vana-parool-123";
    private static final String NEW_PASSWORD = "Uus-Parool-582";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData testData;
    @Autowired
    private PinService pinService;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;

    private Member member;
    private String pin;
    private String clientIp;

    @BeforeEach
    void createMember() {
        int n = COUNTER.incrementAndGet();
        pin = "74" + String.format("%04d", n);
        member = testData.member("change-pw-" + n + "@example.org", pinService.hash(pin));
        member.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        clientIp = "10.81." + (n / 250) + "." + (n % 250 + 1);
    }

    @Test
    void change_withCorrectCurrentPassword_newWorksAndOldDoesNot() throws Exception {
        changePassword(passwordSession(OLD_PASSWORD), OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(OLD_PASSWORD).andExpect(status().isForbidden());
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void change_wrongCurrentPassword_returns403WithRemainingAttemptsAndKeepsPassword() throws Exception {
        changePassword(passwordSession(OLD_PASSWORD), "wrong-password-1", NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(124))
                .andExpect(jsonPath("$.remainingAttempts").value(4));

        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void change_newPasswordTooShort_returns400AndKeepsPassword() throws Exception {
        changePassword(passwordSession(OLD_PASSWORD), OLD_PASSWORD, "123456789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void change_newPasswordOver72Bytes_returns400() throws Exception {
        changePassword(passwordSession(OLD_PASSWORD), OLD_PASSWORD, "A!b" + "äö".repeat(17) + "ä")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("must be at most 72 bytes"));
    }

    @Test
    void change_newPasswordWithoutSpecialCharacter_returns400AndKeepsPassword() throws Exception {
        changePassword(passwordSession(OLD_PASSWORD), OLD_PASSWORD, "UusParool582")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("must contain a special character"));

        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void change_currentPasswordMissing_returns400() throws Exception {
        mockMvc.perform(put("/api/me/password").with(csrf()).session(passwordSession(OLD_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
    }

    @Test
    void change_memberWithoutPassword_returns403() throws Exception {
        MockHttpSession pinSession = pinSession();
        member.setPasswordHash(null);
        entityManager.flush();

        changePassword(pinSession, "anything-at-all", NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(124));
    }

    @Test
    void change_inPinSessionWithCorrectCurrentPassword_works() throws Exception {
        changePassword(pinSession(), OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void change_fifthWrongCurrentPassword_locksAndThenRefusesEvenCorrectOne() throws Exception {
        MockHttpSession session = passwordSession(OLD_PASSWORD);
        for (int i = 0; i < 4; i++) {
            changePassword(session, "wrong-password-" + i, NEW_PASSWORD).andExpect(status().isForbidden());
        }

        changePassword(session, "wrong-password-5", NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value(117));
        changePassword(session, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());
        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void change_lockoutDoesNotBlockLogin() throws Exception {
        MockHttpSession session = passwordSession(OLD_PASSWORD);
        for (int i = 0; i < 5; i++) {
            changePassword(session, "wrong-password-" + i, NEW_PASSWORD);
        }

        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void change_otherSessionsStayValid() throws Exception {
        MockHttpSession otherSession = passwordSession(OLD_PASSWORD);

        changePassword(passwordSession(OLD_PASSWORD), OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").session(otherSession)).andExpect(status().isOk());
    }

    @Test
    void change_withoutSession_returns401() throws Exception {
        mockMvc.perform(put("/api/me/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(115));
    }

    @Test
    void change_withoutCsrfToken_returns403AndKeepsPassword() throws Exception {
        mockMvc.perform(put("/api/me/password").session(passwordSession(OLD_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));

        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void change_neverLogsPasswords(CapturedOutput output) throws Exception {
        MockHttpSession session = passwordSession(OLD_PASSWORD);
        changePassword(session, "wrong-password-x", NEW_PASSWORD);
        changePassword(session, OLD_PASSWORD, NEW_PASSWORD);

        // Only the app's log lines: MockMvc's own printout of the test request may show the request body
        String appLog = output.getOut().lines().filter(line -> line.contains("--- [revkassa]"))
                .reduce("", (all, line) -> all + line + "\n");
        assertThat(appLog).contains("Password changed: memberId=" + member.getId())
                .doesNotContain(OLD_PASSWORD).doesNotContain(NEW_PASSWORD).doesNotContain("wrong-password-x");
    }

    // ---- helpers ----

    private ResultActions changePassword(MockHttpSession session, String currentPassword, String newPassword) throws Exception {
        ResultActions result = mockMvc.perform(put("/api/me/password").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body(currentPassword, newPassword)));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private static String body(String currentPassword, String newPassword) {
        return "{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\"" + newPassword + "\"}";
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).with(fromIp(clientIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + member.getEmail() + "\",\"password\":\"" + password + "\"}"));
    }

    private MockHttpSession passwordSession(String password) throws Exception {
        return sessionOf(login(password).andExpect(status().isOk()).andReturn());
    }

    private MockHttpSession pinSession() throws Exception {
        String deviceToken = "change-pw-tablet-" + member.getId();
        testData.trustedDevice("Tahvel", deviceService.hash(deviceToken), member);
        MvcResult result = mockMvc.perform(post("/api/auth/pin").with(csrf()).cookie(new Cookie("REVKASSA_DEVICE", deviceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return sessionOf(result);
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
