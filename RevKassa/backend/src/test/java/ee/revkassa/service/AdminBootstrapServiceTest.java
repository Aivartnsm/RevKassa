package ee.revkassa.service;

import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevelRepository;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B06: the first admin is created on startup only when there is no active admin.
 * The dev database has admins, so tests that need "no admin" deactivate them inside the rolled-back transaction.
 */
@SpringBootTest
@Transactional
@Import(TestData.class)
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapServiceTest {

    private static final String EMAIL = "bootstrap-admin@example.org";
    private static final String PASSWORD = "Bootstrap-Pass-91x";
    private static final Pattern LOGGED_PIN = Pattern.compile("PIN \\(shown only once\\): (\\d{6})");

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MembershipLevelRepository membershipLevelRepository;
    @Autowired
    private PinService pinService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TestData testData;

    @Test
    void activeAdminExists_doesNothingEvenWithSettings(CapturedOutput output) {
        testData.member("existing-admin@example.org", "bootstrap-pin-hmac-1").setRole(MemberRoleEnum.ADMIN);
        long before = memberRepository.count();

        service(EMAIL, PASSWORD, "Esimene Admin").createFirstAdminIfMissing();

        assertThat(memberRepository.count()).isEqualTo(before);
        assertThat(memberRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(output).doesNotContain("First admin created");
    }

    @Test
    void noActiveAdmin_createsAdminWithPasswordAndLoggedPin(CapturedOutput output) {
        deactivateAllAdmins();

        service("  " + EMAIL + " ", PASSWORD, " Esimene Admin ").createFirstAdminIfMissing();

        Member admin = memberRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(MemberRoleEnum.ADMIN);
        assertThat(admin.getActive()).isTrue();
        assertThat(admin.getDisplayName()).isEqualTo("Esimene Admin");
        assertThat(admin.getBalanceCents()).isZero();
        assertThat(admin.getLevel().getId()).isEqualTo(membershipLevelRepository.findAllActive().getFirst().getId());
        assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        String loggedPin = loggedPin(output);
        assertThat(admin.getPinHmac()).isEqualTo(pinService.hash(loggedPin));
        assertThat(pinService.findByPin(loggedPin)).get().extracting(Member::getId).isEqualTo(admin.getId());
    }

    @Test
    void neverLogsPassword(CapturedOutput output) {
        deactivateAllAdmins();

        service(EMAIL, PASSWORD, "Esimene Admin").createFirstAdminIfMissing();

        assertThat(output).doesNotContain(PASSWORD);
    }

    @Test
    void runsOnlyOnce_secondStartupSkips(CapturedOutput output) {
        deactivateAllAdmins();
        AdminBootstrapService service = service(EMAIL, PASSWORD, "Esimene Admin");

        service.createFirstAdminIfMissing();
        long afterFirst = memberRepository.count();
        service.createFirstAdminIfMissing();

        assertThat(memberRepository.count()).isEqualTo(afterFirst);
        assertThat(LOGGED_PIN.matcher(output.getOut()).results().count()).isEqualTo(1);
    }

    @Test
    void blankName_usesDefaultName() {
        deactivateAllAdmins();

        service(EMAIL, PASSWORD, "").createFirstAdminIfMissing();

        assertThat(memberRepository.findByEmail(EMAIL)).get().extracting(Member::getDisplayName).isEqualTo("Admin");
    }

    @Test
    void noActiveAdminAndNoSettings_createsNothingAndWarns(CapturedOutput output) {
        deactivateAllAdmins();
        long before = memberRepository.count();

        service("", "", "").createFirstAdminIfMissing();

        assertThat(memberRepository.count()).isEqualTo(before);
        assertThat(output).contains("No active admin");
    }

    @Test
    void shortPassword_stopsStartupAndCreatesNothing() {
        deactivateAllAdmins();
        long before = memberRepository.count();

        assertThatThrownBy(() -> service(EMAIL, "123456789", "Admin").createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 10")
                .hasMessageNotContaining("123456789");
        assertThat(memberRepository.count()).isEqualTo(before);
    }

    @Test
    void passwordBreakingRules_stopsStartupNamingTheRule() {
        deactivateAllAdmins();

        assertThatThrownBy(() -> service(EMAIL, "bootstrap-pass-91x", "Admin").createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must contain an uppercase letter")
                .hasMessageNotContaining("bootstrap-pass-91x");
    }

    @Test
    void notAnEmail_stopsStartup() {
        deactivateAllAdmins();

        assertThatThrownBy(() -> service("not-an-email", PASSWORD, "Admin").createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emailOfExistingMember_stopsStartupAndDoesNotPromoteThem() {
        Member member = testData.member("taken@example.org", "bootstrap-pin-hmac-2");
        deactivateAllAdmins();

        assertThatThrownBy(() -> service("TAKEN@example.org", PASSWORD, "Admin").createFirstAdminIfMissing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already belongs");
        assertThat(memberRepository.findById(member.getId())).get()
                .extracting(Member::getRole).isEqualTo(MemberRoleEnum.MEMBER);
    }

    private AdminBootstrapService service(String email, String password, String name) {
        return new AdminBootstrapService(memberRepository, membershipLevelRepository, pinService, passwordEncoder,
                clock, email, password, name);
    }

    private void deactivateAllAdmins() {
        entityManager.flush();
        entityManager.createQuery("update Member m set m.active = false where m.role = :role")
                .setParameter("role", MemberRoleEnum.ADMIN)
                .executeUpdate();
        entityManager.clear();
    }

    private static String loggedPin(CapturedOutput output) {
        Matcher matcher = LOGGED_PIN.matcher(output.getOut());
        assertThat(matcher.find()).as("PIN logged once").isTrue();
        return matcher.group(1);
    }
}
