package ee.revkassa.controller.auth;

import ee.revkassa.infrastructure.mail.MailMessage;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.infrastructure.security.TokenService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.passwordtoken.PasswordToken;
import ee.revkassa.persistence.passwordtoken.PasswordTokenPurposeEnum;
import ee.revkassa.persistence.passwordtoken.PasswordTokenRepository;
import ee.revkassa.testdata.FakeMailer;
import ee.revkassa.testdata.TestClock;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B36: invite and reset links, setting the password, through the real security chain, PostgreSQL and a fake Mailer.
 * Every test uses its own email address and IP: rate limits live in memory for the whole test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, FakeMailer.Config.class})
@ExtendWith(OutputCaptureExtension.class)
class PasswordLinkFlowTest {

    private static final String ADMIN_PASSWORD = "admin-password-123";
    private static final String NEW_PASSWORD = "uus-parool-123";
    private static final Pattern LINK = Pattern.compile("http://localhost:5173/set-password\\?token=([A-Za-z0-9_-]{43})\\n");
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(Instant.parse("2026-11-24T10:00:00Z"));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData testData;
    @Autowired
    private FakeMailer fakeMailer;
    @Autowired
    private TestClock clock;
    @Autowired
    private PinService pinService;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PasswordTokenRepository passwordTokenRepository;
    @Autowired
    private EntityManager entityManager;

    private Member member;
    private Member admin;
    private String clientIp;

    @BeforeEach
    void createMembers() {
        int n = COUNTER.incrementAndGet();
        member = testData.member("link-member-" + n + "@example.org", pinService.hash("72" + String.format("%04d", n)));
        admin = testData.member("link-admin-" + n + "@example.org", pinService.hash("73" + String.format("%04d", n)));
        admin.setRole(MemberRoleEnum.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        clientIp = "10.79." + (n / 250) + "." + (n % 250 + 1);
        fakeMailer.clear();
    }

    // ---- invite ----

    @Test
    void invite_sendsEstonianEmailWithOneTimeLink() throws Exception {
        invite(adminSession(), member.getId()).andExpect(status().isNoContent());

        MailMessage email = fakeMailer.last();
        assertThat(email.to()).isEqualTo(member.getEmail());
        assertThat(email.subject()).isEqualTo("RevKassa: määra oma parool");
        assertThat(email.body()).contains("Tere, " + member.getDisplayName() + "!").contains("kehtib 7 päeva");
        PasswordToken saved = savedToken(tokenFrom(email));
        assertThat(saved.getPurpose()).isEqualTo(PasswordTokenPurposeEnum.INVITE);
        assertThat(saved.getMember().getId()).isEqualTo(member.getId());
        assertThat(saved.getCreatedBy().getId()).isEqualTo(admin.getId());
        assertThat(saved.getExpiresAt().toInstant()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
        assertThat(saved.getUsedAt()).isNull();
    }

    @Test
    void invite_storesOnlyHashOfToken() throws Exception {
        invite(adminSession(), member.getId());

        String token = tokenFrom(fakeMailer.last());
        PasswordToken saved = savedToken(token);
        assertThat(saved.getTokenHash()).isEqualTo(tokenService.hash(token)).isNotEqualTo(token);
    }

    @Test
    void invite_byMember_returns403AndSendsNothing() throws Exception {
        member.setPasswordHash(passwordEncoder.encode(NEW_PASSWORD));

        invite(passwordSession(member.getEmail(), NEW_PASSWORD), admin.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void invite_inactiveMember_returns403() throws Exception {
        member.setActive(false);

        invite(adminSession(), member.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(112));
        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void invite_unknownMember_returns404() throws Exception {
        invite(adminSession(), Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    // That nothing is saved on a mail failure is tested without a test transaction in PasswordLinkRollbackTest
    @Test
    void invite_mailServerFails_returns503() throws Exception {
        fakeMailer.failNextSend();

        invite(adminSession(), member.getId())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(123));
    }

    // ---- reset request ----

    @Test
    void reset_activeMember_sendsResetLinkValidOneHour() throws Exception {
        requestReset(member.getEmail()).andExpect(status().isNoContent());

        MailMessage email = fakeMailer.last();
        assertThat(email.to()).isEqualTo(member.getEmail());
        assertThat(email.subject()).isEqualTo("RevKassa: parooli lähtestamine");
        assertThat(email.body()).contains("kehtib 1 tund");
        PasswordToken saved = savedToken(tokenFrom(email));
        assertThat(saved.getPurpose()).isEqualTo(PasswordTokenPurposeEnum.RESET);
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getExpiresAt().toInstant()).isEqualTo(clock.instant().plus(Duration.ofHours(1)));
    }

    @Test
    void reset_emailInOtherCase_sendsToStoredAddress() throws Exception {
        requestReset(member.getEmail().toUpperCase()).andExpect(status().isNoContent());

        assertThat(fakeMailer.last().to()).isEqualTo(member.getEmail());
    }

    @Test
    void reset_unknownEmail_returns204AndSendsNothing() throws Exception {
        requestReset("nobody-" + COUNTER.incrementAndGet() + "@example.org").andExpect(status().isNoContent());

        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void reset_inactiveMember_returns204AndSendsNothing() throws Exception {
        member.setActive(false);

        requestReset(member.getEmail()).andExpect(status().isNoContent());

        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void reset_malformedEmail_returns400() throws Exception {
        requestReset("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));
    }

    @Test
    void reset_sixthRequestFromSameIp_returns429() throws Exception {
        for (int i = 0; i < 5; i++) {
            requestReset("nobody-" + i + "-" + COUNTER.incrementAndGet() + "@example.org").andExpect(status().isNoContent());
        }

        requestReset(member.getEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.errorCode").value(122))
                .andExpect(jsonPath("$.nextLockoutSeconds").doesNotExist());
        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void reset_sixthEmailToSameAddressFromOtherIps_returns204ButSendsNothing() throws Exception {
        for (int i = 0; i < 5; i++) {
            clientIp = "10.80." + COUNTER.get() % 250 + "." + (i + 1);
            requestReset(member.getEmail()).andExpect(status().isNoContent());
        }
        clientIp = "10.80." + COUNTER.get() % 250 + ".100";

        requestReset(member.getEmail()).andExpect(status().isNoContent());

        assertThat(fakeMailer.sent()).hasSize(5);
    }

    @Test
    void reset_mailServerFails_stillReturns204() throws Exception {
        fakeMailer.failNextSend();

        requestReset(member.getEmail()).andExpect(status().isNoContent());

        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void newLink_makesOlderLinkInvalid() throws Exception {
        requestReset(member.getEmail());
        String oldToken = tokenFrom(fakeMailer.last());
        requestReset(member.getEmail());
        String newToken = tokenFrom(fakeMailer.last());

        setPassword(oldToken, NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(121));
        setPassword(newToken, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    // ---- set password ----

    @Test
    void setPassword_thenPasswordLoginWorks() throws Exception {
        requestReset(member.getEmail());

        setPassword(tokenFrom(fakeMailer.last()), NEW_PASSWORD).andExpect(status().isNoContent());

        loginWithPassword(member.getEmail(), NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.getId()));
    }

    @Test
    void setPassword_replacesOldPassword() throws Exception {
        member.setPasswordHash(passwordEncoder.encode("vana-parool-123"));
        requestReset(member.getEmail());

        setPassword(tokenFrom(fakeMailer.last()), NEW_PASSWORD).andExpect(status().isNoContent());

        loginWithPassword(member.getEmail(), "vana-parool-123").andExpect(status().isForbidden());
    }

    @Test
    void setPassword_doesNotLogIn() throws Exception {
        requestReset(member.getEmail());

        MvcResult result = setPassword(tokenFrom(fakeMailer.last()), NEW_PASSWORD).andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    }

    @Test
    void setPassword_linkWorksOnlyOnce() throws Exception {
        requestReset(member.getEmail());
        String token = tokenFrom(fakeMailer.last());
        setPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        setPassword(token, "teine-parool-123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(121));
        assertThat(savedToken(token).getUsedAt()).isNotNull();
    }

    @Test
    void setPassword_resetLinkJustBeforeExpiry_works() throws Exception {
        requestReset(member.getEmail());
        clock.advance(Duration.ofMinutes(59));

        setPassword(tokenFrom(fakeMailer.last()), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void setPassword_resetLinkAfterOneHour_returns403() throws Exception {
        requestReset(member.getEmail());
        clock.advance(Duration.ofHours(1));

        setPassword(tokenFrom(fakeMailer.last()), NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(121));
    }

    @Test
    void setPassword_inviteAfterSevenDays_returns403() throws Exception {
        invite(adminSession(), member.getId());
        clock.advance(Duration.ofDays(7).plusSeconds(1));

        setPassword(tokenFrom(fakeMailer.last()), NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(121));
    }

    @Test
    void setPassword_memberDeactivatedAfterLinkSent_returns403() throws Exception {
        requestReset(member.getEmail());
        member.setActive(false);
        entityManager.flush();

        setPassword(tokenFrom(fakeMailer.last()), NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(121));
    }

    @Test
    void setPassword_unknownToken_returns403() throws Exception {
        setPassword("x".repeat(43), NEW_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(121));
    }

    @Test
    void setPassword_tooShortOrTooManyBytes_returns400AndLinkStillWorks() throws Exception {
        requestReset(member.getEmail());
        String token = tokenFrom(fakeMailer.last());

        for (String password : List.of("123456789", "ä".repeat(37))) {
            setPassword(token, password)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(103))
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }
        setPassword(token, "ä".repeat(36)).andExpect(status().isNoContent());
    }

    @Test
    void setPassword_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(post("/api/auth/set-password").contentType(MediaType.APPLICATION_JSON)
                        .content(setPasswordBody("x".repeat(43), NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void neitherTokenNorPasswordIsLogged(CapturedOutput output) throws Exception {
        requestReset(member.getEmail());
        String token = tokenFrom(fakeMailer.last());
        setPassword(token, NEW_PASSWORD);
        setPassword(token, NEW_PASSWORD);

        // Only the app's log lines: MockMvc's own printout of the test request may show the request body
        String appLog = output.getOut().lines().filter(line -> line.contains("--- [revkassa]"))
                .reduce("", (all, line) -> all + line + "\n");
        assertThat(appLog).doesNotContain(token).doesNotContain(NEW_PASSWORD).contains("Password set with link");
    }

    // ---- helpers ----

    private ResultActions invite(MockHttpSession session, Long memberId) throws Exception {
        return mockMvc.perform(post("/api/admin/members/" + memberId + "/invite").with(csrf()).session(session));
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset").with(csrf()).with(fromIp(clientIp))
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions setPassword(String token, String password) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/auth/set-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(setPasswordBody(token, password)));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private static String setPasswordBody(String token, String password) {
        return "{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}";
    }

    private ResultActions loginWithPassword(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).with(fromIp(clientIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private MockHttpSession adminSession() throws Exception {
        return passwordSession(admin.getEmail(), ADMIN_PASSWORD);
    }

    private MockHttpSession passwordSession(String email, String password) throws Exception {
        MvcResult result = loginWithPassword(email, password).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private PasswordToken savedToken(String token) {
        entityManager.flush();
        entityManager.clear();
        return passwordTokenRepository.findForUpdateByTokenHash(tokenService.hash(token)).orElseThrow();
    }

    private static String tokenFrom(MailMessage email) {
        Matcher matcher = LINK.matcher(email.body());
        assertThat(matcher.find()).as("link in email body").isTrue();
        return matcher.group(1);
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
