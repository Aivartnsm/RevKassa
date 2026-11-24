package ee.revkassa.controller.admin.device;

import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.trusteddevice.TrustedDevice;
import ee.revkassa.persistence.trusteddevice.TrustedDeviceRepository;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B38: device registration, list and revoke through the real security filter chain and PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestData.class)
class AdminDeviceFlowTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

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
    private TrustedDeviceRepository trustedDeviceRepository;
    @Autowired
    private EntityManager entityManager;

    private Member admin;
    private Member member;
    private String clientIp;

    @BeforeEach
    void createAdminAndMember() {
        admin = testData.member("device-flow-admin@example.org", pinService.hash("710001"));
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRole(MemberRoleEnum.ADMIN);
        member = testData.member("device-flow-member@example.org", pinService.hash("710002"));
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        clientIp = "10.78.0." + IP_COUNTER.incrementAndGet();
    }

    // ---- register ----

    @Test
    void register_setsDeviceCookieAndStoresOnlyHash() throws Exception {
        MvcResult result = register(adminSession(), "  Baaritahvel  ", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Baaritahvel"))
                .andExpect(jsonPath("$.createdByName").value(admin.getDisplayName()))
                .andExpect(jsonPath("$.revokedAt").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(cookie().httpOnly("REVKASSA_DEVICE", true))
                .andExpect(cookie().maxAge("REVKASSA_DEVICE", 400 * 24 * 60 * 60))
                .andReturn();

        String token = result.getResponse().getCookie("REVKASSA_DEVICE").getValue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
        TrustedDevice saved = trustedDeviceRepository.findById(idOf(result)).orElseThrow();
        assertThat(saved.getTokenHash()).isEqualTo(deviceService.hash(token)).isNotEqualTo(token);
        assertThat(saved.getCreatedBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    void register_thenPinLoginWorksWithTheNewCookie() throws Exception {
        String token = register(adminSession(), "Baaritahvel", null).andReturn()
                .getResponse().getCookie("REVKASSA_DEVICE").getValue();

        mockMvc.perform(post("/api/auth/pin").with(csrf()).cookie(new Cookie("REVKASSA_DEVICE", token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"710002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()));
    }

    @Test
    void register_twiceGivesDifferentTokens() throws Exception {
        String first = register(adminSession(), "Tahvel 1", null).andReturn().getResponse().getCookie("REVKASSA_DEVICE").getValue();
        String second = register(adminSession(), "Tahvel 2", null).andReturn().getResponse().getCookie("REVKASSA_DEVICE").getValue();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void register_onAlreadyTrustedBrowser_returns403AndCreatesNothing() throws Exception {
        testData.trustedDevice("Olemasolev", deviceService.hash("existing-token"), admin);
        long before = trustedDeviceRepository.count();

        register(adminSession(), "Teine nimi", "existing-token")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(120))
                .andExpect(cookie().doesNotExist("REVKASSA_DEVICE"));

        assertThat(trustedDeviceRepository.count()).isEqualTo(before);
    }

    @Test
    void register_onBrowserWithRevokedCookie_isAllowed() throws Exception {
        TrustedDevice old = testData.trustedDevice("Vana", deviceService.hash("revoked-token"), admin);
        testData.revoke(old, admin);

        register(adminSession(), "Uus", "revoked-token")
                .andExpect(status().isCreated());
    }

    @Test
    void register_blankOrTooLongName_returns400() throws Exception {
        MockHttpSession session = adminSession();
        for (String body : List.of("{\"name\":\"   \"}", "{}", "{\"name\":\"" + "x".repeat(101) + "\"}")) {
            mockMvc.perform(post("/api/admin/devices").with(csrf()).session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(103))
                    .andExpect(jsonPath("$.fieldErrors.name").exists());
        }
    }

    @Test
    void register_byMemberPasswordSession_returns403() throws Exception {
        register(passwordSession("device-flow-member@example.org"), "Tahvel", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118))
                .andExpect(cookie().doesNotExist("REVKASSA_DEVICE"));
    }

    @Test
    void register_byAdminPinSession_returns403() throws Exception {
        testData.trustedDevice("Tahvel", deviceService.hash("pin-tablet-token"), admin);
        MvcResult pinLogin = mockMvc.perform(post("/api/auth/pin").with(csrf())
                        .cookie(new Cookie("REVKASSA_DEVICE", "pin-tablet-token"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"710001\"}"))
                .andExpect(status().isOk()).andReturn();

        register((MockHttpSession) pinLogin.getRequest().getSession(false), "Teine tahvel", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void register_withoutSession_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/devices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Tahvel\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(115));
    }

    @Test
    void register_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/devices").session(adminSession())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Tahvel\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- list ----

    @Test
    void list_showsActiveFirstWithAdminNamesAndNoTokens() throws Exception {
        TrustedDevice revoked = testData.trustedDevice("Vana tahvel", deviceService.hash("list-token-1"), admin);
        testData.revoke(revoked, admin);
        TrustedDevice active = testData.trustedDevice("Baaritahvel", deviceService.hash("list-token-2"), admin);

        mockMvc.perform(get("/api/admin/devices").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")].createdByName").value(admin.getDisplayName()))
                .andExpect(jsonPath("$[?(@.id == " + revoked.getId() + ")].revokedByName").value(admin.getDisplayName()))
                .andExpect(jsonPath("$[0].revokedAt").doesNotExist())
                .andExpect(jsonPath("$[*].tokenHash").doesNotExist());
    }

    @Test
    void list_byMemberSession_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/devices").session(passwordSession("device-flow-member@example.org")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- revoke ----

    @Test
    void revoke_marksDeviceRevokedAndPinLoginStopsThere() throws Exception {
        TrustedDevice tablet = testData.trustedDevice("Tahvel", deviceService.hash("revoke-token"), admin);

        revoke(adminSession(), tablet.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedAt").exists())
                .andExpect(jsonPath("$.revokedByName").value(admin.getDisplayName()));

        entityManager.flush();
        entityManager.refresh(tablet);
        assertThat(tablet.getRevokedAt()).isNotNull();
        assertThat(tablet.getRevokedBy().getId()).isEqualTo(admin.getId());
        mockMvc.perform(post("/api/auth/pin").with(csrf()).cookie(new Cookie("REVKASSA_DEVICE", "revoke-token"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"710002\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(116));
    }

    @Test
    void revoke_alreadyRevoked_returns403AndKeepsFirstRevocation() throws Exception {
        TrustedDevice tablet = testData.trustedDevice("Tahvel", deviceService.hash("double-revoke-token"), admin);
        MockHttpSession session = adminSession();
        revoke(session, tablet.getId()).andExpect(status().isOk());
        String firstRevokedAt = tablet.getRevokedAt().toString();

        revoke(session, tablet.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(119));

        assertThat(tablet.getRevokedAt().toString()).isEqualTo(firstRevokedAt);
    }

    @Test
    void revoke_unknownDevice_returns404() throws Exception {
        revoke(adminSession(), Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    void revoke_malformedId_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/devices/abc/revoke").with(csrf()).session(adminSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    @Test
    void revoke_byMemberSession_returns403AndDeviceStaysTrusted() throws Exception {
        TrustedDevice tablet = testData.trustedDevice("Tahvel", deviceService.hash("member-revoke-token"), admin);

        revoke(passwordSession("device-flow-member@example.org"), tablet.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));

        assertThat(tablet.getRevokedAt()).isNull();
    }

    // ---- helpers ----

    private ResultActions register(MockHttpSession session, String name,
                                   String existingDeviceToken) throws Exception {
        var request = post("/api/admin/devices").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}");
        if (existingDeviceToken != null) {
            request.cookie(new Cookie("REVKASSA_DEVICE", existingDeviceToken));
        }
        return mockMvc.perform(request);
    }

    private ResultActions revoke(MockHttpSession session, Long deviceId) throws Exception {
        return mockMvc.perform(post("/api/admin/devices/" + deviceId + "/revoke").with(csrf()).session(session));
    }

    private MockHttpSession adminSession() throws Exception {
        return passwordSession("device-flow-admin@example.org");
    }

    private MockHttpSession passwordSession(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).with(fromIp(clientIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static Long idOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
