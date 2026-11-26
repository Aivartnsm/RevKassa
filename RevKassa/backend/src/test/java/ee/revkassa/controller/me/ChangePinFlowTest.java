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
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B34: PUT /api/me/pin through the real security chain and PostgreSQL. Every test has its own members, PINs and
 * trusted device, because lockouts live in memory for the whole test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestData.class)
@ExtendWith(OutputCaptureExtension.class)
class ChangePinFlowTest {

    private static final String PASSWORD = "vana-parool-123";
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
    private String oldPin;
    private String newPin;
    private String deviceToken;

    @BeforeEach
    void createMemberAndDevice() {
        int n = COUNTER.incrementAndGet();
        oldPin = "75" + String.format("%04d", n);
        newPin = "76" + String.format("%04d", n);
        member = testData.member("change-pin-" + n + "@example.org", pinService.hash(oldPin));
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        deviceToken = "change-pin-tablet-" + n;
        testData.trustedDevice("Tahvel", deviceService.hash(deviceToken), member);
    }

    @Test
    void change_withCorrectCurrentPin_newWorksAndOldDoesNot() throws Exception {
        changePin(pinSession(), oldPin, newPin).andExpect(status().isNoContent());

        Member saved = entityManager.find(Member.class, member.getId());
        assertThat(saved.getPinHmac()).isEqualTo(pinService.hash(newPin));
        assertThat(saved.getPinChangedAt()).isNotNull();
        pinLogin(oldPin).andExpect(status().isForbidden());
        pinLogin(newPin).andExpect(status().isOk());
    }

    @Test
    void change_inPasswordSession_works() throws Exception {
        changePin(passwordSession(), oldPin, newPin).andExpect(status().isNoContent());

        pinLogin(newPin).andExpect(status().isOk());
    }

    @Test
    void change_wrongCurrentPin_returns403WithRemainingAttemptsAndKeepsPin() throws Exception {
        changePin(pinSession(), "000001", newPin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(143))
                .andExpect(jsonPath("$.remainingAttempts").value(4));

        pinLogin(oldPin).andExpect(status().isOk());
    }

    @Test
    void change_newPinNotSixDigits_returns400AndKeepsPin() throws Exception {
        MockHttpSession session = pinSession();

        changePin(session, oldPin, "12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103))
                .andExpect(jsonPath("$.fieldErrors.newPin").value("PIN must be exactly 6 digits"));
        changePin(session, oldPin, "12a456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPin").exists());
        changePin(session, oldPin, "1234567").andExpect(status().isBadRequest());

        pinLogin(oldPin).andExpect(status().isOk());
    }

    @Test
    void change_currentPinMissing_returns400() throws Exception {
        mockMvc.perform(put("/api/me/pin").with(csrf()).session(pinSession())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPin\":\"" + newPin + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPin").exists());
    }

    @Test
    void change_newPinSameAsCurrent_returns403AndIsNotCountedAsFailure() throws Exception {
        MockHttpSession session = pinSession();

        changePin(session, oldPin, oldPin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(145))
                .andExpect(jsonPath("$.remainingAttempts").doesNotExist());

        changePin(session, "000001", newPin).andExpect(jsonPath("$.remainingAttempts").value(4));
    }

    @Test
    void change_newPinTakenByAnotherMember_returns403WithoutOwnerAndCountsAsFailure() throws Exception {
        Member other = testData.member("change-pin-other-" + member.getId() + "@example.org", pinService.hash(newPin));

        changePin(pinSession(), oldPin, newPin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(144))
                .andExpect(jsonPath("$.remainingAttempts").value(4))
                .andExpect(content().string(not(containsString(other.getEmail()))))
                .andExpect(content().string(not(containsString(other.getDisplayName()))));

        assertThat(entityManager.find(Member.class, member.getId()).getPinHmac()).isEqualTo(pinService.hash(oldPin));
        assertThat(entityManager.find(Member.class, other.getId()).getPinHmac()).isEqualTo(pinService.hash(newPin));
    }

    @Test
    void change_fifthFailure_locksAndThenRefusesEvenCorrectPin() throws Exception {
        MockHttpSession session = passwordSession();
        for (int i = 0; i < 4; i++) {
            changePin(session, "00000" + i, newPin).andExpect(status().isForbidden());
        }

        changePin(session, "000009", newPin)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value(113));
        changePin(session, oldPin, newPin).andExpect(status().isTooManyRequests());
        assertThat(entityManager.find(Member.class, member.getId()).getPinHmac()).isEqualTo(pinService.hash(oldPin));
    }

    @Test
    void change_takenPinsAndWrongPinsShareTheCounter() throws Exception {
        testData.member("change-pin-taken-" + member.getId() + "@example.org", pinService.hash(newPin));
        MockHttpSession session = passwordSession();
        for (int i = 0; i < 4; i++) {
            changePin(session, oldPin, newPin).andExpect(jsonPath("$.errorCode").value(144));
        }

        changePin(session, "000009", "123123").andExpect(status().isTooManyRequests());
    }

    @Test
    void change_lockoutDoesNotBlockPinLogin() throws Exception {
        MockHttpSession session = passwordSession();
        for (int i = 0; i < 5; i++) {
            changePin(session, "00000" + i, newPin);
        }

        pinLogin(oldPin).andExpect(status().isOk());
    }

    @Test
    void change_withoutSession_returns401() throws Exception {
        mockMvc.perform(put("/api/me/pin").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(oldPin, newPin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(115));
    }

    @Test
    void change_withoutCsrfToken_returns403AndKeepsPin() throws Exception {
        mockMvc.perform(put("/api/me/pin").session(pinSession())
                        .contentType(MediaType.APPLICATION_JSON).content(body(oldPin, newPin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));

        assertThat(entityManager.find(Member.class, member.getId()).getPinHmac()).isEqualTo(pinService.hash(oldPin));
    }

    @Test
    void change_neverLogsPins(CapturedOutput output) throws Exception {
        MockHttpSession session = passwordSession();
        changePin(session, "098765", newPin);
        changePin(session, oldPin, newPin);

        // Only the app's log lines: MockMvc's own printout of the test request may show the request body
        String appLog = output.getOut().lines().filter(line -> line.contains("--- [revkassa]"))
                .reduce("", (all, line) -> all + line + "\n");
        assertThat(appLog).contains("PIN changed: memberId=" + member.getId())
                .doesNotContain(oldPin).doesNotContain(newPin).doesNotContain("098765");
    }

    // ---- helpers ----

    private ResultActions changePin(MockHttpSession session, String currentPin, String pin) throws Exception {
        ResultActions result = mockMvc.perform(put("/api/me/pin").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body(currentPin, pin)));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private static String body(String currentPin, String pin) {
        return "{\"currentPin\":\"" + currentPin + "\",\"newPin\":\"" + pin + "\"}";
    }

    private ResultActions pinLogin(String pin) throws Exception {
        return mockMvc.perform(post("/api/auth/pin").with(csrf()).cookie(new Cookie("REVKASSA_DEVICE", deviceToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"" + pin + "\"}"));
    }

    private MockHttpSession pinSession() throws Exception {
        return sessionOf(pinLogin(oldPin).andExpect(status().isOk()).andReturn());
    }

    private MockHttpSession passwordSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("10.82.0." + (member.getId() % 250 + 1));
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + member.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return sessionOf(result);
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
