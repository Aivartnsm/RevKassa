package ee.revkassa.controller.auth;

import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.trusteddevice.TrustedDevice;
import ee.revkassa.testdata.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login, session, CSRF and access rules through the real Spring Security filter chain and PostgreSQL.
 * Every test creates its own members and devices; the transaction is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestData.class)
class LoginFlowTest {

    private static final String PASSWORD = "correct-horse-battery";
    // No endpoint lives here yet: 404 means security let the request through, 401/403 means it did not
    private static final String ADMIN_PATH = "/api/admin/anything";

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

    // Lockouts live in memory for the whole test run, so every test logs in from its own IP
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    private Member member;
    private Member admin;
    private TrustedDevice tablet;
    private String clientIp;

    @BeforeEach
    void createMembersAndTablet() {
        member = testData.member("flow-member@example.org", pinService.hash("700001"));
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        member.setBalanceCents(-2740L);
        admin = testData.member("flow-admin@example.org", pinService.hash("700002"));
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRole(MemberRoleEnum.ADMIN);
        tablet = testData.trustedDevice("Flow tahvel", deviceService.hash("flow-tablet-token"), admin);
        clientIp = "10.77.0." + IP_COUNTER.incrementAndGet();
    }

    // ---- PIN ----

    @Test
    void pinLogin_onTrustedDevice_returnsMemberAndStartsOneMinuteSession() throws Exception {
        MvcResult result = loginWithPin("700001", "flow-tablet-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.displayName").value(member.getDisplayName()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.balanceCents").value(-2740))
                .andExpect(jsonPath("$.loginMethod").value("PIN"))
                .andExpect(jsonPath("$.pinHmac").doesNotExist())
                .andReturn();

        assertThat(sessionOf(result).getMaxInactiveInterval()).isEqualTo(60);
        assertThat(tablet.getLastUsedAt()).isNotNull();
    }

    @Test
    void pinLogin_renewsDeviceCookie() throws Exception {
        loginWithPin("700001", "flow-tablet-token")
                .andExpect(cookie().value("REVKASSA_DEVICE", "flow-tablet-token"))
                .andExpect(cookie().maxAge("REVKASSA_DEVICE", 400 * 24 * 60 * 60))
                .andExpect(cookie().httpOnly("REVKASSA_DEVICE", true));
    }

    @Test
    void pinLogin_withoutDeviceCookie_returns403EvenWithCorrectPin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/pin").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"700001\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(116))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void pinLogin_unknownDeviceToken_returns403() throws Exception {
        loginWithPin("700001", "made-up-token")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(116));
    }

    @Test
    void pinLogin_revokedDevice_returns403AndDoesNotTouchDevice() throws Exception {
        testData.revoke(tablet, admin);

        loginWithPin("700001", "flow-tablet-token")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(116));

        assertThat(tablet.getLastUsedAt()).isNull();
    }

    @Test
    void pinLogin_wrongPin_returns403AndNoSession() throws Exception {
        MvcResult result = loginWithPin("799999", "flow-tablet-token")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(111))
                .andExpect(jsonPath("$.remainingAttempts").value(4))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(tablet.getLastUsedAt()).isNull();
    }

    @Test
    void pinLogin_inactiveMember_returns403AndNoSession() throws Exception {
        member.setActive(false);

        MvcResult result = loginWithPin("700001", "flow-tablet-token")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(112))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void pinLogin_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/auth/pin").cookie(new Cookie("REVKASSA_DEVICE", "flow-tablet-token"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"700001\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void pinLogin_replacesExistingSessionId() throws Exception {
        MockHttpSession oldSession = new MockHttpSession();

        MvcResult result = mockMvc.perform(post("/api/auth/pin").with(csrf()).session(oldSession)
                        .cookie(new Cookie("REVKASSA_DEVICE", "flow-tablet-token"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"700001\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(oldSession.isInvalid()).isTrue();
        assertThat(sessionOf(result).getId()).isNotEqualTo(oldSession.getId());
    }

    @Test
    void login_givesNewCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/pin").with(csrf())
                        .cookie(new Cookie("REVKASSA_DEVICE", "flow-tablet-token"),
                                new Cookie("XSRF-TOKEN", "token-known-before-login"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"700001\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().value("XSRF-TOKEN", not("token-known-before-login")))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false)); // the SPA must be able to read it
    }

    @Test
    void pinSession_ofAdmin_isOnlyMemberSession() throws Exception {
        MockHttpSession session = sessionOf(loginWithPin("700002", "flow-tablet-token")
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn());

        mockMvc.perform(get(ADMIN_PATH).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void pinLogin_fifthWrongPin_locksDeviceWith429() throws Exception {
        wrongPinTimes(4).andExpect(jsonPath("$.remainingAttempts").value(1));

        loginWithPin("799999", "flow-tablet-token")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value(113))
                .andExpect(jsonPath("$.nextLockoutSeconds").value(300))
                .andExpect(jsonPath("$.remainingAttempts").doesNotExist());
    }

    @Test
    void pinLogin_whileLocked_rejectsEvenCorrectPin() throws Exception {
        wrongPinTimes(5);

        MvcResult result = loginWithPin("700001", "flow-tablet-token")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value(113))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void pinLockout_doesNotAffectOtherDevice() throws Exception {
        testData.trustedDevice("Teine tahvel", deviceService.hash("flow-second-tablet"), admin);
        wrongPinTimes(5);

        loginWithPin("700001", "flow-second-tablet")
                .andExpect(status().isOk());
    }

    @Test
    void pinLogin_success_resetsAttempts() throws Exception {
        wrongPinTimes(3);
        loginWithPin("700001", "flow-tablet-token").andExpect(status().isOk());

        loginWithPin("799999", "flow-tablet-token")
                .andExpect(jsonPath("$.remainingAttempts").value(4));
    }

    @Test
    void pinLogin_inactiveMemberAndUntrustedDevice_doNotCountAsWrongPins() throws Exception {
        member.setActive(false);
        for (int i = 0; i < 5; i++) {
            loginWithPin("700001", "flow-tablet-token").andExpect(jsonPath("$.errorCode").value(112));
            loginWithPin("799999", "made-up-token").andExpect(jsonPath("$.errorCode").value(116));
        }

        loginWithPin("799999", "flow-tablet-token")
                .andExpect(jsonPath("$.remainingAttempts").value(4));
    }

    // ---- password ----

    @Test
    void passwordLogin_member_returnsMemberAndStartsThirtyMinuteSession() throws Exception {
        MvcResult result = loginWithPassword("flow-member@example.org", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.loginMethod").value("PASSWORD"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        assertThat(sessionOf(result).getMaxInactiveInterval()).isEqualTo(30 * 60);
    }

    @Test
    void passwordLogin_emailIsCaseInsensitive() throws Exception {
        loginWithPassword("FLOW-Member@Example.org", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()));
    }

    @Test
    void passwordSession_ofMember_cannotAccessAdminEndpoints() throws Exception {
        MockHttpSession session = sessionOf(loginWithPassword("flow-member@example.org", PASSWORD).andReturn());

        mockMvc.perform(get(ADMIN_PATH).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void passwordSession_ofAdmin_passesAdminSecurity() throws Exception {
        MockHttpSession session = sessionOf(loginWithPassword("flow-admin@example.org", PASSWORD).andReturn());

        mockMvc.perform(get(ADMIN_PATH).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginMethod").value("PASSWORD"));
    }

    @Test
    void passwordLogin_wrongPassword_returns403AndNoSession() throws Exception {
        MvcResult result = loginWithPassword("flow-member@example.org", "wrong-password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(114))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void passwordLogin_unknownEmail_givesSameAnswerAsWrongPassword() throws Exception {
        loginWithPassword("nobody@example.org", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(114))
                .andExpect(jsonPath("$.message").value("Wrong email or password."));
    }

    @Test
    void passwordLogin_passwordNotSet_givesSameAnswerAsWrongPassword() throws Exception {
        member.setPasswordHash(null);

        loginWithPassword("flow-member@example.org", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(114))
                .andExpect(jsonPath("$.message").value("Wrong email or password."));
    }

    @Test
    void passwordLogin_inactiveMember_returns403AndNoSession() throws Exception {
        member.setActive(false);

        MvcResult result = loginWithPassword("flow-member@example.org", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(112))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void passwordLogin_overlongPassword_returns403NotServerError() throws Exception {
        // bcrypt reads at most 72 bytes; 100 two-byte characters must not crash the encoder
        loginWithPassword("flow-member@example.org", "ä".repeat(100))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(114));
    }

    @Test
    void passwordLogin_fifthFailureFromSameIp_returns429() throws Exception {
        for (int i = 0; i < 4; i++) {
            loginWithPassword("nobody@example.org", PASSWORD).andExpect(status().isForbidden());
        }

        loginWithPassword("flow-member@example.org", "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value(117))
                .andExpect(jsonPath("$.nextLockoutSeconds").value(300));
        loginWithPassword("flow-member@example.org", PASSWORD)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordLockout_doesNotAffectOtherIpOrPinLogin() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginWithPassword("flow-member@example.org", "wrong-password");
        }
        clientIp = clientIp + "0";

        loginWithPassword("flow-member@example.org", PASSWORD).andExpect(status().isOk());
        loginWithPin("700001", "flow-tablet-token").andExpect(status().isOk());
    }

    // ---- /api/me ----

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(115))
                .andExpect(jsonPath("$.message").value("Session expired or missing."));
    }

    @Test
    void me_withExpiredSession_returns401() throws Exception {
        MockHttpSession session = sessionOf(loginWithPin("700001", "flow-tablet-token").andReturn());
        session.invalidate(); // what the servlet container does after the inactivity timeout

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(115));
    }

    @Test
    void me_readsFreshBalanceFromDatabase() throws Exception {
        MockHttpSession session = sessionOf(loginWithPin("700001", "flow-tablet-token").andReturn());
        member.setBalanceCents(450L);

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.balanceCents").value(450))
                .andExpect(jsonPath("$.loginMethod").value("PIN"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void me_afterMemberDeactivated_returns403AndEndsSession() throws Exception {
        MockHttpSession session = sessionOf(loginWithPassword("flow-member@example.org", PASSWORD).andReturn());
        member.setActive(false);

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(112));

        assertThat(session.isInvalid()).isTrue();
    }

    // ---- logout ----

    @Test
    void logout_endsSessionAndClearsSessionCookie() throws Exception {
        MockHttpSession session = sessionOf(loginWithPassword("flow-admin@example.org", PASSWORD).andReturn());

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().doesNotExist("REVKASSA_DEVICE"));

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get(ADMIN_PATH).session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_removesCsrfTokenCookie() throws Exception {
        MockHttpSession session = sessionOf(loginWithPin("700001", "flow-tablet-token").andReturn());

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session)
                        .cookie(new Cookie("XSRF-TOKEN", "token-of-previous-member")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
    }

    @Test
    void logout_withoutSession_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_withoutCsrfToken_returns403AndKeepsSession() throws Exception {
        MockHttpSession session = sessionOf(loginWithPin("700001", "flow-tablet-token").andReturn());

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden());

        assertThat(session.isInvalid()).isFalse();
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void afterLogout_pinWorksAgainOnSameDevice() throws Exception {
        MockHttpSession session = sessionOf(loginWithPin("700001", "flow-tablet-token").andReturn());
        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session));

        loginWithPin("700002", "flow-tablet-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(admin.getId()));
    }

    private ResultActions loginWithPin(String pin, String deviceToken) throws Exception {
        return mockMvc.perform(post("/api/auth/pin").with(csrf())
                .cookie(new Cookie("REVKASSA_DEVICE", deviceToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"" + pin + "\"}"));
    }

    private ResultActions loginWithPassword(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).with(fromIp(clientIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions wrongPinTimes(int times) throws Exception {
        ResultActions last = null;
        for (int i = 0; i < times; i++) {
            last = loginWithPin("799999", "flow-tablet-token");
        }
        return last;
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
