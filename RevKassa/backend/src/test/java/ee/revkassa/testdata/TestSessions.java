package ee.revkassa.testdata;

import ee.revkassa.infrastructure.security.DeviceService;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logged-in sessions through the real login endpoints, for tests that need a member or an admin.
 * Use with @AutoConfigureMockMvc, @Transactional and @Import({TestData.class, TestSessions.class}).
 * Every login comes from its own IP and every member has its own email and PIN, because lockouts and rate limits
 * live in memory for the whole test run.
 */
@TestComponent
@RequiredArgsConstructor
public class TestSessions {

    public static final String PASSWORD = "Test-Parool-58!";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final MockMvc mockMvc;
    private final TestData testData;
    private final PinService pinService;
    private final DeviceService deviceService;
    private final PasswordEncoder passwordEncoder;

    public Member createMember(MemberRoleEnum role) {
        int n = COUNTER.incrementAndGet();
        Member member = testData.member("session-" + role.name().toLowerCase() + "-" + n + "@example.org",
                pinService.hash(pinService.generateUniquePin()));
        member.setRole(role);
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return member;
    }

    public MockHttpSession adminSession() throws Exception {
        return passwordSession(createMember(MemberRoleEnum.ADMIN));
    }

    public MockHttpSession memberSession() throws Exception {
        return passwordSession(createMember(MemberRoleEnum.MEMBER));
    }

    public MockHttpSession passwordSession(Member member) throws Exception {
        String ip = "10.90." + (COUNTER.get() / 250 % 250) + "." + (COUNTER.incrementAndGet() % 250 + 1);
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + member.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /**
     * A PIN session on a new trusted tablet: member rights only, even for an admin.
     */
    public MockHttpSession pinSession(MemberRoleEnum role) throws Exception {
        String pin = pinService.generateUniquePin();
        Member member = createMember(role);
        member.setPinHmac(pinService.hash(pin));
        String deviceToken = "session-tablet-" + COUNTER.incrementAndGet();
        testData.trustedDevice("Test tahvel", deviceService.hash(deviceToken), member);
        MvcResult result = mockMvc.perform(post("/api/auth/pin").with(csrf())
                        .cookie(new Cookie(DeviceService.DEVICE_COOKIE_NAME, deviceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
