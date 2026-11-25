package ee.revkassa.controller.admin.organisation;

import ee.revkassa.infrastructure.mail.MailMessage;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.organisation.Organisation;
import ee.revkassa.testdata.FakeMailer;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B39: GET and PUT /api/admin/organisation through the real security chain and PostgreSQL, with a fake mailer.
 * Each test starts from a known row (the dev data may hold anything).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class, FakeMailer.Config.class})
class AdminOrganisationFlowTest {

    private static final String OLD_IBAN = "EE382200221020145685";
    private static final String NEW_IBAN = "DE89370400440532013000";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private FakeMailer fakeMailer;
    @Autowired
    private EntityManager entityManager;

    private Member admin;
    private MockHttpSession adminSession;

    @BeforeEach
    void startFromKnownDetails() throws Exception {
        Organisation organisation = entityManager.find(Organisation.class, Organisation.ID);
        organisation.setName("MTÜ Vana Nimi");
        organisation.setIban(OLD_IBAN);
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        admin.setDisplayName("Admin Muutja");
        adminSession = testSessions.passwordSession(admin);
        forget();
        fakeMailer.clear();
    }

    // ---- reading ----

    @Test
    void get_returnsNameIbanAndVersion() throws Exception {
        mockMvc.perform(get("/api/admin/organisation").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MTÜ Vana Nimi"))
                .andExpect(jsonPath("$.iban").value(OLD_IBAN))
                .andExpect(jsonPath("$.version").value(currentVersion()));
    }

    // ---- saving ----

    @Test
    void put_savesIbanWithoutSpacesInCapitalsAndTrimmedName() throws Exception {
        long version = currentVersion();

        save(adminSession, "  MTÜ Uus Nimi ", "de89 3704 0044 0532 0130 00", version)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MTÜ Uus Nimi"))
                .andExpect(jsonPath("$.iban").value(NEW_IBAN))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        Organisation saved = entityManager.find(Organisation.class, Organisation.ID);
        assertThat(saved.getIban()).isEqualTo(NEW_IBAN);
        assertThat(saved.getUpdatedBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    void staleVersion_returns409AndChangesNothing() throws Exception {
        long version = currentVersion();
        save(adminSession, "MTÜ Esimene", OLD_IBAN, version).andExpect(status().isOk());

        save(adminSession, "MTÜ Teine", NEW_IBAN, version)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(105));

        assertDetails("MTÜ Esimene", OLD_IBAN);
        assertThat(fakeMailer.sent()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EE382200221020145686", "EE38220022102014568", "EE38-2200-2210-2014-5685", "", " "})
    void invalidIban_returns400WithFieldErrorAndChangesNothing(String iban) throws Exception {
        save(adminSession, "MTÜ Uus Nimi", iban, currentVersion())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103))
                .andExpect(jsonPath("$.fieldErrors.iban").exists());

        assertDetails("MTÜ Vana Nimi", OLD_IBAN);
        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void invalidName_returns400() throws Exception {
        save(adminSession, " ", NEW_IBAN, currentVersion())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        save(adminSession, "x".repeat(101), NEW_IBAN, currentVersion())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void missingVersion_returns400() throws Exception {
        send(adminSession, "{\"name\":\"MTÜ Uus Nimi\",\"iban\":\"" + NEW_IBAN + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());
    }

    // ---- email to admins ----

    @Test
    void ibanChange_emailsEveryActiveAdminWithOldAndNewIban() throws Exception {
        Member otherAdmin = testSessions.createMember(MemberRoleEnum.ADMIN);
        Member inactiveAdmin = testSessions.createMember(MemberRoleEnum.ADMIN);
        inactiveAdmin.setActive(false);
        Member member = testSessions.createMember(MemberRoleEnum.MEMBER);
        forget();

        save(adminSession, "MTÜ Vana Nimi", NEW_IBAN, currentVersion()).andExpect(status().isOk());

        assertThat(fakeMailer.sent()).extracting(MailMessage::to)
                .containsExactlyInAnyOrderElementsOf(memberRepository.findActiveAdmins().stream().map(Member::getEmail).toList())
                .contains(admin.getEmail(), otherAdmin.getEmail())
                .doesNotContain(inactiveAdmin.getEmail(), member.getEmail());
        MailMessage email = fakeMailer.sent().stream().filter(sent -> sent.to().equals(otherAdmin.getEmail())).findFirst().orElseThrow();
        assertThat(email.subject()).isEqualTo("RevKassa: ühingu IBAN muudeti");
        assertThat(email.body())
                .contains("Admin Muutja muutis")
                .contains("Vana IBAN: EE38 2200 2210 2014 5685")
                .contains("Uus IBAN:  DE89 3704 0044 0532 0130 00")
                .contains("MTÜ Vana Nimi");
    }

    @Test
    void firstIbanEver_emailSaysThereWasNone() throws Exception {
        Organisation organisation = entityManager.find(Organisation.class, Organisation.ID);
        organisation.setIban(null);
        forget();

        save(adminSession, "MTÜ Vana Nimi", NEW_IBAN, currentVersion()).andExpect(status().isOk());

        assertThat(fakeMailer.last().body()).contains("Vana IBAN: (puudus)");
    }

    @Test
    void sameIbanWrittenDifferently_sendsNoEmail() throws Exception {
        save(adminSession, "MTÜ Uus Nimi", "ee38 2200 2210 2014 5685", currentVersion()).andExpect(status().isOk());

        assertDetails("MTÜ Uus Nimi", OLD_IBAN);
        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void failedEmail_doesNotUndoTheChange() throws Exception {
        fakeMailer.failNextSend();

        save(adminSession, "MTÜ Vana Nimi", NEW_IBAN, currentVersion()).andExpect(status().isOk());

        assertDetails("MTÜ Vana Nimi", NEW_IBAN);
        assertThat(fakeMailer.sent()).hasSize(memberRepository.findActiveAdmins().size() - 1);
    }

    // ---- who ----

    @Test
    void member_gets403() throws Exception {
        MockHttpSession memberSession = testSessions.memberSession();

        mockMvc.perform(get("/api/admin/organisation").session(memberSession)).andExpect(status().isForbidden());
        save(memberSession, "MTÜ Häkker", NEW_IBAN, currentVersion()).andExpect(status().isForbidden());

        assertDetails("MTÜ Vana Nimi", OLD_IBAN);
    }

    @Test
    void adminWithPinSession_gets403() throws Exception {
        save(testSessions.pinSession(MemberRoleEnum.ADMIN), "MTÜ Häkker", NEW_IBAN, currentVersion())
                .andExpect(status().isForbidden());

        assertDetails("MTÜ Vana Nimi", OLD_IBAN);
    }

    @Test
    void withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/organisation")).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/organisation").session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(body("MTÜ Häkker", NEW_IBAN, currentVersion())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));

        assertDetails("MTÜ Vana Nimi", OLD_IBAN);
    }

    // ---- helpers ----

    private ResultActions save(MockHttpSession session, String name, String iban, long version) throws Exception {
        return send(session, body(name, iban, version));
    }

    private static String body(String name, String iban, long version) {
        return "{\"name\":\"" + name + "\",\"iban\":\"" + iban + "\",\"version\":" + version + "}";
    }

    private ResultActions send(MockHttpSession session, String body) throws Exception {
        ResultActions result = mockMvc.perform(put("/api/admin/organisation").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
        forget();
        return result;
    }

    private long currentVersion() {
        return entityManager.find(Organisation.class, Organisation.ID).getVersion();
    }

    private void assertDetails(String name, String iban) {
        forget();
        Organisation organisation = entityManager.find(Organisation.class, Organisation.ID);
        assertThat(organisation.getName()).isEqualTo(name);
        assertThat(organisation.getIban()).isEqualTo(iban);
    }

    // All requests share the test transaction: write and forget, as a commit would between real requests
    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
