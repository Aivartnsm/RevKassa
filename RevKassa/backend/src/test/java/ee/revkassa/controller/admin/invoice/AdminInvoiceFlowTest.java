package ee.revkassa.controller.admin.invoice;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.infrastructure.mail.MailMessage;
import ee.revkassa.persistence.invoice.Invoice;
import ee.revkassa.persistence.invoice.InvoiceStatusEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.organisation.Organisation;
import ee.revkassa.service.AccountPosting;
import ee.revkassa.service.AccountService;
import ee.revkassa.testdata.FakeMailer;
import ee.revkassa.testdata.TestClock;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B26: invoices through the real security chain and PostgreSQL, with a fake mailer and a clock in 2030 (no dev data
 * invoices in that year, so numbers start from 0001). Tallinn is UTC+3 in summer and UTC+2 in winter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class, FakeMailer.Config.class})
@ExtendWith(OutputCaptureExtension.class)
class AdminInvoiceFlowTest {

    private static final String PAYEE = "MTÜ Testklubi";
    private static final String IBAN = "EE382200221020145685";
    private static final String START = "2030-06-15T09:00:00Z";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(Instant.parse(START));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private AccountService accountService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private FakeMailer fakeMailer;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TestClock clock;

    private Member admin;
    private MockHttpSession adminSession;

    @BeforeEach
    void createAdminAndPaymentDetails() throws Exception {
        // The clock bean is shared by all tests of this class
        moveClockTo(START);
        Organisation organisation = entityManager.find(Organisation.class, Organisation.ID);
        organisation.setName(PAYEE);
        organisation.setIban(IBAN);
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        adminSession = testSessions.passwordSession(admin);
        forget();
        fakeMailer.clear();
    }

    // ---- sending ----

    @Test
    void send_memberInDebt_savesSentInvoiceAndEmailsPaymentDetails() throws Exception {
        Member debtor = debtor("Kaspar Võlgnik", 1250);

        send(debtor.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(debtor.getId()))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[0].skipReason").doesNotExist())
                .andExpect(jsonPath("$[0].invoice.number").value("2030-0001"))
                .andExpect(jsonPath("$[0].invoice.amountCents").value(1250))
                .andExpect(jsonPath("$[0].invoice.dueDate").value("2030-06-29"))
                .andExpect(jsonPath("$[0].invoice.emailTo").value(debtor.getEmail()))
                .andExpect(jsonPath("$[0].invoice.sentAt").exists())
                .andExpect(jsonPath("$[0].invoice.sentByName").value(admin.getDisplayName()));

        MailMessage email = fakeMailer.last();
        assertThat(email.to()).isEqualTo(debtor.getEmail());
        assertThat(email.subject()).isEqualTo("Arve 2030-0001 · " + PAYEE);
        assertThat(email.body())
                .startsWith("Tere, Kaspar Võlgnik!")
                .contains("Tasumisele kuulub:  12,50 €")
                .contains("Maksetähtaeg:       29.06.2030")
                .contains("Saaja:     " + PAYEE)
                .contains("IBAN:      EE38 2200 2210 2014 5685")
                .contains("Selgitus:  Arve 2030-0001");
        assertThat(invoicesOf(debtor)).singleElement().satisfies(invoice -> {
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatusEnum.SENT);
            assertThat(invoice.getError()).isNull();
            assertThat(invoice.getSentBy().getId()).isEqualTo(admin.getId());
        });
    }

    @Test
    void send_neverChangesBalances() throws Exception {
        Member debtor = debtor("Saldo Muutumatu", 1250);

        send(debtor.getId()).andExpect(status().isOk());

        Member saved = memberRepository.findById(debtor.getId()).orElseThrow();
        assertThat(saved.getBalanceCents()).isEqualTo(-1250);
        assertThat(ledgerSum(debtor)).isEqualTo(-1250);
    }

    @Test
    void send_numbersRunOnAndStartAgainAtNewYearInTallinnTime() throws Exception {
        Member first = debtor("Esimene", 100);
        Member second = debtor("Teine", 200);

        send(first.getId(), second.getId())
                .andExpect(jsonPath("$[0].invoice.number").value("2030-0001"))
                .andExpect(jsonPath("$[1].invoice.number").value("2030-0002"));

        moveClockTo("2030-12-31T21:59:00Z"); // 23:59 in Tallinn
        send(first.getId()).andExpect(jsonPath("$[0].invoice.number").value("2030-0003"))
                .andExpect(jsonPath("$[0].invoice.dueDate").value("2031-01-14"));

        moveClockTo("2030-12-31T22:01:00Z"); // 00:01 on 1 January in Tallinn, still 2030 in UTC
        send(second.getId()).andExpect(jsonPath("$[0].invoice.number").value("2031-0001"));
        send(first.getId()).andExpect(jsonPath("$[0].invoice.number").value("2031-0002"));
    }

    @Test
    void send_failedAddress_savesFailedInvoiceAndDoesNotStopOthers() throws Exception {
        Member badAddress = debtor("Vale Aadress", 500);
        Member goodAddress = debtor("Hea Aadress", 700);
        fakeMailer.failNextSend();

        send(badAddress.getId(), goodAddress.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].invoice.number").value("2030-0001"))
                .andExpect(jsonPath("$[0].invoice.error").value("Email could not be sent: test failure"))
                .andExpect(jsonPath("$[0].invoice.sentAt").doesNotExist())
                .andExpect(jsonPath("$[1].status").value("SENT"))
                .andExpect(jsonPath("$[1].invoice.number").value("2030-0002"));

        assertThat(fakeMailer.sent()).extracting(MailMessage::to).containsExactly(goodAddress.getEmail());
        assertThat(invoicesOf(badAddress)).singleElement()
                .extracting(Invoice::getStatus).isEqualTo(InvoiceStatusEnum.FAILED);
    }

    @Test
    void send_membersNotInDebtOrUnknown_areSkippedWithReason() throws Exception {
        Member zero = testSessions.createMember(MemberRoleEnum.MEMBER);
        Member prepaid = debtor("Ettemaksja", -800);
        Member debtor = debtor("Võlgnik", 300);

        send(zero.getId(), prepaid.getId(), Long.MAX_VALUE, debtor.getId(), debtor.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$[0].skipReason").value("NOT_IN_DEBT"))
                .andExpect(jsonPath("$[0].invoice").doesNotExist())
                .andExpect(jsonPath("$[1].skipReason").value("NOT_IN_DEBT"))
                .andExpect(jsonPath("$[2].memberId").value(Long.MAX_VALUE))
                .andExpect(jsonPath("$[2].skipReason").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$[3].status").value("SENT"));

        assertThat(invoicesOf(zero)).isEmpty();
        assertThat(invoicesOf(prepaid)).isEmpty();
        assertThat(invoicesOf(debtor)).hasSize(1);
        assertThat(fakeMailer.sent()).hasSize(1);
    }

    @Test
    void send_inactiveMemberInDebt_isInvoiced() throws Exception {
        Member former = debtor("Endine Liige", 400);
        deactivate(former);

        send(former.getId()).andExpect(jsonPath("$[0].status").value("SENT"));
    }

    @Test
    void send_withoutPaymentDetails_returns404AndSendsNothing() throws Exception {
        Member debtor = debtor("Ootaja", 300);
        entityManager.find(Organisation.class, Organisation.ID).setIban(null);
        forget();

        send(debtor.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(131));

        assertThat(fakeMailer.sent()).isEmpty();
        assertThat(invoicesOf(debtor)).isEmpty();
    }

    @Test
    void send_emptyOrTooLongList_returns400() throws Exception {
        sendBody("{\"memberIds\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103))
                .andExpect(jsonPath("$.fieldErrors.memberIds").exists());
        sendBody("{}").andExpect(status().isBadRequest());
        String tooMany = LongStream.rangeClosed(1, 201).mapToObj(String::valueOf).collect(Collectors.joining(","));
        sendBody("{\"memberIds\":[" + tooMany + "]}").andExpect(status().isBadRequest());
        sendBody("{\"memberIds\":[null]}").andExpect(status().isBadRequest());

        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void send_byMemberOrAdminPinSession_returns403() throws Exception {
        Member debtor = debtor("Turvaline", 300);

        mockMvc.perform(post("/api/admin/invoices").with(csrf()).session(testSessions.memberSession())
                        .contentType(MediaType.APPLICATION_JSON).content(body(debtor.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/invoices").with(csrf()).session(testSessions.pinSession(MemberRoleEnum.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body(debtor.getId())))
                .andExpect(status().isForbidden());

        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void send_withoutCsrfToken_returns403() throws Exception {
        Member debtor = debtor("Csrf", 300);

        mockMvc.perform(post("/api/admin/invoices").session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(body(debtor.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void send_logsIdsAndNumberButNotAddressOrAmount(CapturedOutput output) throws Exception {
        Member debtor = debtor("Logi Kontroll", 4321);

        send(debtor.getId());

        String appLog = output.getOut().lines().filter(line -> line.contains("--- [revkassa]"))
                .filter(line -> line.contains("Invoice"))
                .collect(Collectors.joining("\n"));
        assertThat(appLog).contains("number=2030-0001", "memberId=" + debtor.getId())
                .doesNotContain(debtor.getEmail()).doesNotContain("4321").doesNotContain("43,21");
    }

    // ---- email content ----

    @Test
    void email_linesStartAtMost60DaysBackAndAddUpToTheDebt() throws Exception {
        Member debtor = testSessions.createMember(MemberRoleEnum.MEMBER);
        debtor.setDisplayName("Ridade Kontroll");
        moveClockTo("2030-04-01T09:00:00Z");
        adjust(debtor, -500, "vana võlg");
        moveClockTo("2030-06-05T09:00:00Z");
        adjust(debtor, -1000, "uus võlg");
        moveClockTo("2030-06-10T09:00:00Z");
        adjust(debtor, 300, "osaline tasu");
        moveClockTo("2030-06-15T09:00:00Z");

        send(debtor.getId()).andExpect(jsonPath("$[0].invoice.amountCents").value(1200));

        List<String> lines = balanceLinesOf(fakeMailer.last());
        assertThat(lines).hasSize(4);
        assertLine(lines.get(0), "16.04.2030", "Algsaldo", "−5,00 €");
        assertLine(lines.get(1), "05.06.2030", "Parandus · uus võlg", "−10,00 €");
        assertLine(lines.get(2), "10.06.2030", "Parandus · osaline tasu", "3,00 €");
        assertLine(lines.get(3), "15.06.2030", "Lõppsaldo", "−12,00 €");
    }

    @Test
    void email_linesStartAtTheLastInvoiceThatReachedTheMember() throws Exception {
        Member debtor = debtor("Teine Arve", 1000);
        send(debtor.getId());
        moveClockTo("2030-06-20T09:00:00Z");
        adjust(debtor, -250, "pärast arvet");
        moveClockTo("2030-06-25T09:00:00Z");

        send(debtor.getId()).andExpect(jsonPath("$[0].invoice.amountCents").value(1250));

        List<String> lines = balanceLinesOf(fakeMailer.last());
        assertThat(lines).hasSize(3);
        assertLine(lines.get(0), "15.06.2030", "Algsaldo", "−10,00 €");
        assertLine(lines.get(1), "20.06.2030", "Parandus · pärast arvet", "−2,50 €");
        assertLine(lines.get(2), "25.06.2030", "Lõppsaldo", "−12,50 €");
    }

    @Test
    void email_failedInvoiceDoesNotMoveTheStartOfTheLines() throws Exception {
        Member debtor = debtor("Ebaõnnestunud Enne", 1000);
        moveClockTo("2030-06-20T09:00:00Z");
        fakeMailer.failNextSend();
        send(debtor.getId()).andExpect(jsonPath("$[0].status").value("FAILED"));
        moveClockTo("2030-06-25T09:00:00Z");

        send(debtor.getId()).andExpect(jsonPath("$[0].status").value("SENT"));

        // The debt was posted on 15.06, before the failed invoice: still listed
        assertThat(balanceLinesOf(fakeMailer.last()))
                .anySatisfy(line -> assertLine(line, "15.06.2030", "Parandus · algvõlg", "−10,00 €"));
    }

    // ---- resending ----

    @Test
    void resend_failedInvoice_sendsSameNumberToCurrentAddress() throws Exception {
        Member debtor = debtor("Kirjaviga", 900);
        fakeMailer.failNextSend();
        Long invoiceId = invoiceIdOf(send(debtor.getId()));
        debtor = memberRepository.findById(debtor.getId()).orElseThrow();
        debtor.setEmail("parandatud-" + debtor.getId() + "@example.org");
        forget();
        clock.advance(Duration.ofHours(2));

        resend(invoiceId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId))
                .andExpect(jsonPath("$.number").value("2030-0001"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.amountCents").value(900))
                .andExpect(jsonPath("$.dueDate").value("2030-06-29"))
                .andExpect(jsonPath("$.emailTo").value("parandatud-" + debtor.getId() + "@example.org"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.sentAt").exists());

        assertThat(fakeMailer.last().to()).isEqualTo("parandatud-" + debtor.getId() + "@example.org");
        assertThat(fakeMailer.last().subject()).isEqualTo("Arve 2030-0001 · " + PAYEE);
        assertThat(invoicesOf(debtor)).hasSize(1);
    }

    @Test
    void resend_failingAgain_returnsInvoiceStillFailed() throws Exception {
        Member debtor = debtor("Jälle Viga", 900);
        fakeMailer.failNextSend();
        Long invoiceId = invoiceIdOf(send(debtor.getId()));
        fakeMailer.failNextSend();

        resend(invoiceId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("Email could not be sent: test failure"));
    }

    @Test
    void resend_sentInvoice_returns403AndSendsNothing() throws Exception {
        Member debtor = debtor("Juba Saadetud", 900);
        Long invoiceId = invoiceIdOf(send(debtor.getId()));
        fakeMailer.clear();

        resend(invoiceId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(147));

        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void resend_memberHasPaidMeanwhile_returns403() throws Exception {
        Member debtor = debtor("Maksis Ära", 900);
        fakeMailer.failNextSend();
        Long invoiceId = invoiceIdOf(send(debtor.getId()));
        adjust(debtor, 900, "makse");

        resend(invoiceId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(146));
    }

    @Test
    void resend_unknownInvoice_returns404() throws Exception {
        resend(Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value(101));
    }

    // ---- candidates ----

    @Test
    void candidates_listDebtorsWithLastInvoiceAndDefaultSelection() throws Exception {
        Member neverInvoiced = debtor("Pole Arvet", 100);
        Member recentlyInvoiced = debtor("Hiljuti Arve", 200);
        Member onlyFailed = debtor("Ainult Viga", 300);
        Member former = debtor("Endine", 400);
        deactivate(former);
        Member prepaid = debtor("Plussis", -100);
        send(recentlyInvoiced.getId());
        fakeMailer.failNextSend();
        send(onlyFailed.getId());

        String json = candidates().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(candidate(json, neverInvoiced, "defaultSelected")).isEqualTo(true);
        assertThat(candidate(json, neverInvoiced, "lastInvoiceAt")).isNull();
        assertThat(candidate(json, neverInvoiced, "balanceCents")).isEqualTo(-100);
        assertThat(candidate(json, neverInvoiced, "email")).isEqualTo(neverInvoiced.getEmail());
        assertThat(candidate(json, recentlyInvoiced, "defaultSelected")).isEqualTo(false);
        assertThat(candidate(json, recentlyInvoiced, "lastInvoiceStatus")).isEqualTo("SENT");
        assertThat(candidate(json, onlyFailed, "defaultSelected")).isEqualTo(true);
        assertThat(candidate(json, onlyFailed, "lastInvoiceStatus")).isEqualTo("FAILED");
        assertThat(candidate(json, former, "active")).isEqualTo(false);
        assertThat(JsonPath.<List<Object>>read(json, "$[?(@.memberId == " + prepaid.getId() + ")]")).isEmpty();
    }

    @Test
    void candidates_afterSevenDaysAreSelectedAgain() throws Exception {
        Member debtor = debtor("Nädal Möödas", 200);
        send(debtor.getId());
        clock.advance(Duration.ofDays(7).plusMinutes(1));

        String json = candidates().andReturn().getResponse().getContentAsString();

        assertThat(candidate(json, debtor, "defaultSelected")).isEqualTo(true);
    }

    @Test
    void candidates_byMember_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/invoices/candidates").session(testSessions.memberSession()))
                .andExpect(status().isForbidden());
    }

    // ---- preview ----

    @Test
    void preview_isTheEmailThatWouldBeSentAndSavesNothing() throws Exception {
        Member debtor = debtor("Eelvaade", 1500);

        String json = mockMvc.perform(get("/api/admin/invoices/preview").param("memberId", debtor.getId().toString())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("2030-0001"))
                .andExpect(jsonPath("$.amountCents").value(1500))
                .andExpect(jsonPath("$.dueDate").value("2030-06-29"))
                .andExpect(jsonPath("$.to").value(debtor.getEmail()))
                .andExpect(jsonPath("$.subject").value("Arve 2030-0001 · " + PAYEE))
                .andReturn().getResponse().getContentAsString();
        assertThat(fakeMailer.sent()).isEmpty();
        assertThat(invoicesOf(debtor)).isEmpty();

        send(debtor.getId());

        assertThat(fakeMailer.last().body()).isEqualTo(JsonPath.read(json, "$.body"));
    }

    @Test
    void preview_memberNotInDebt_returns403() throws Exception {
        Member member = testSessions.createMember(MemberRoleEnum.MEMBER);

        preview(member.getId().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(146));
    }

    @Test
    void preview_unknownOrMissingMember_returns404Or400() throws Exception {
        preview(String.valueOf(Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
        mockMvc.perform(get("/api/admin/invoices/preview").session(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
        preview("abc").andExpect(status().isBadRequest());
    }

    // ---- history ----

    @Test
    void list_byMember_newestFirstWithPaging() throws Exception {
        Member debtor = debtor("Ajalugu", 100);
        Member other = debtor("Keegi Teine", 100);
        send(debtor.getId());
        clock.advance(Duration.ofDays(1));
        fakeMailer.failNextSend();
        send(debtor.getId(), other.getId());

        mockMvc.perform(get("/api/admin/invoices").param("memberId", debtor.getId().toString()).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices.length()").value(2))
                .andExpect(jsonPath("$.invoices[0].number").value("2030-0002"))
                .andExpect(jsonPath("$.invoices[0].status").value("FAILED"))
                .andExpect(jsonPath("$.invoices[0].memberName").value("Ajalugu"))
                .andExpect(jsonPath("$.invoices[1].number").value("2030-0001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/admin/invoices").param("page", "-1").session(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_withoutMember_includesAllMembers() throws Exception {
        Member first = debtor("Kõik Üks", 100);
        Member second = debtor("Kõik Kaks", 100);
        send(first.getId(), second.getId());

        mockMvc.perform(get("/api/admin/invoices").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices[0].number").value("2030-0002"))
                .andExpect(jsonPath("$.invoices[1].number").value("2030-0001"));
    }

    // ---- helpers ----

    // A member whose balance is -debtCents (a negative debt is a prepayment), posted at the current test time
    private Member debtor(String name, long debtCents) {
        Member member = testSessions.createMember(MemberRoleEnum.MEMBER);
        member.setDisplayName(name);
        adjust(member, -debtCents, "algvõlg");
        return member;
    }

    private void adjust(Member member, long amountCents, String note) {
        accountService.post(AccountPosting.adjustment(member, amountCents, note, admin));
        forget();
    }

    // Through a freshly loaded row: saving the test's own copy would write back its old balance
    private void deactivate(Member member) {
        memberRepository.findById(member.getId()).orElseThrow().setActive(false);
        forget();
    }

    private void moveClockTo(String instant) {
        clock.advance(Duration.between(clock.instant(), Instant.parse(instant)));
    }

    private ResultActions send(Long... memberIds) throws Exception {
        return perform(post("/api/admin/invoices").contentType(MediaType.APPLICATION_JSON).content(body(memberIds)));
    }

    private ResultActions sendBody(String json) throws Exception {
        return perform(post("/api/admin/invoices").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(Long... memberIds) {
        return "{\"memberIds\":[" + Arrays.stream(memberIds).map(String::valueOf).collect(Collectors.joining(",")) + "]}";
    }

    private ResultActions resend(Long invoiceId) throws Exception {
        return perform(post("/api/admin/invoices/" + invoiceId + "/resend"));
    }

    private ResultActions candidates() throws Exception {
        return perform(get("/api/admin/invoices/candidates"));
    }

    private ResultActions preview(String memberId) throws Exception {
        return perform(get("/api/admin/invoices/preview").param("memberId", memberId));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request.with(csrf()).session(adminSession));
        // All requests share the test transaction: write and forget, as a commit would between real requests
        forget();
        return result;
    }

    private static Long invoiceIdOf(ResultActions sendResult) throws Exception {
        return ((Number) JsonPath.read(sendResult.andReturn().getResponse().getContentAsString(), "$[0].invoice.invoiceId"))
                .longValue();
    }

    private static Object candidate(String json, Member member, String field) {
        List<Object> values = JsonPath.read(json, "$[?(@.memberId == " + member.getId() + ")]." + field);
        assertThat(values).as("candidate %s", member.getDisplayName()).hasSize(1);
        return values.getFirst();
    }

    private List<Invoice> invoicesOf(Member member) {
        return entityManager.createQuery("select i from Invoice i join fetch i.sentBy where i.member.id = :memberId order by i.id",
                        Invoice.class)
                .setParameter("memberId", member.getId())
                .getResultList();
    }

    private long ledgerSum(Member member) {
        return entityManager.createQuery("select coalesce(sum(e.amountCents), 0) from AccountEntry e where e.member.id = :memberId",
                        Long.class)
                .setParameter("memberId", member.getId())
                .getSingleResult();
    }

    // The lines between "Saldo muutused" and the empty line after them
    private static List<String> balanceLinesOf(MailMessage email) {
        List<String> lines = email.body().lines().toList();
        int start = lines.indexOf("Saldo muutused") + 1;
        return lines.subList(start, lines.subList(start, lines.size()).indexOf("") + start);
    }

    private static void assertLine(String line, String date, String description, String amount) {
        assertThat(line).startsWith(date + "  " + description).endsWith(" " + amount);
    }

    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
