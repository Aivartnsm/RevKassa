package ee.revkassa.controller.payment;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.organisation.Organisation;
import ee.revkassa.persistence.payment.Payment;
import ee.revkassa.persistence.payment.PaymentMethodEnum;
import ee.revkassa.persistence.payment.PaymentStatusEnum;
import ee.revkassa.service.PaymentService;
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

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B15: payment details, reporting a payment and the member's own payments, through the real security chain and
 * PostgreSQL. A reported payment must leave the balance and the ledger untouched. Real clock: payments reported in the
 * same moment are ordered by id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class PaymentFlowTest {

    private static final String IBAN = "EE382200221020145685";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private Member member;
    private MockHttpSession memberSession;

    @BeforeEach
    void createMemberAndDetails() throws Exception {
        Organisation organisation = entityManager.find(Organisation.class, Organisation.ID);
        organisation.setName("MTÜ Testklubi");
        organisation.setIban(IBAN);
        member = testSessions.createMember(MemberRoleEnum.MEMBER);
        member.setDisplayName("Kaspar Kask");
        memberSession = testSessions.passwordSession(member);
        forget();
    }

    // ---- payment details ----

    @Test
    void details_returnRecipientIbanAndDescriptionWithMembersName() throws Exception {
        mockMvc.perform(get("/api/payments/details").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipient").value("MTÜ Testklubi"))
                .andExpect(jsonPath("$.iban").value(IBAN))
                .andExpect(jsonPath("$.description").value("RevKassa - Kaspar Kask"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "iban"})
    void detailsNotSet_returns404With131(String missing) throws Exception {
        Organisation organisation = entityManager.find(Organisation.class, Organisation.ID);
        if (missing.equals("name")) {
            organisation.setName(null);
        } else {
            organisation.setIban(null);
        }
        forget();

        mockMvc.perform(get("/api/payments/details").session(memberSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(131));
    }

    @Test
    void details_workInPinSession() throws Exception {
        mockMvc.perform(get("/api/payments/details").session(testSessions.pinSession(MemberRoleEnum.MEMBER)))
                .andExpect(status().isOk());
    }

    // ---- reporting ----

    @Test
    void report_createsPendingBankTransferAndLeavesBalanceAndLedgerUntouched() throws Exception {
        long balanceBefore = balanceOf(member);

        report(memberSession, "{\"amountCents\":2500,\"reference\":\"  RevKassa - Kaspar Kask \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").isNumber())
                .andExpect(jsonPath("$.amountCents").value(2500))
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reference").value("RevKassa - Kaspar Kask"))
                .andExpect(jsonPath("$.reportedAt").isNotEmpty())
                .andExpect(jsonPath("$.decidedAt").value(nullValue()))
                .andExpect(jsonPath("$.decisionNote").value(nullValue()));

        assertThat(paymentsOf(member)).singleElement().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatusEnum.PENDING);
            assertThat(payment.getMethod()).isEqualTo(PaymentMethodEnum.BANK_TRANSFER);
            assertThat(payment.getDecidedBy()).isNull();
        });
        assertThat(balanceOf(member)).isEqualTo(balanceBefore);
        assertThat(ledgerRowsOf(member)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"amountCents\":500}", "{\"amountCents\":500,\"reference\":null}",
            "{\"amountCents\":500,\"reference\":\"   \"}"})
    void noOrBlankReference_isSavedAsNone(String body) throws Exception {
        report(memberSession, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(nullValue()));
    }

    @Test
    void largeAmount_isAccepted() throws Exception {
        report(memberSession, "{\"amountCents\":100000000}").andExpect(status().isCreated());
    }

    @Test
    void reference140Characters_isAccepted() throws Exception {
        report(memberSession, "{\"amountCents\":500,\"reference\":\"" + "x".repeat(140) + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void sameReportTwice_makesTwoPendingPayments() throws Exception {
        report(memberSession, "{\"amountCents\":1000,\"reference\":\"oktoober\"}").andExpect(status().isCreated());
        report(memberSession, "{\"amountCents\":1000,\"reference\":\"oktoober\"}").andExpect(status().isCreated());

        assertThat(paymentsOf(member)).hasSize(2);
    }

    @Test
    void memberIdInBody_isIgnored_paymentIsAlwaysYourOwn() throws Exception {
        Member other = testSessions.createMember(MemberRoleEnum.MEMBER);
        forget();

        report(memberSession, "{\"amountCents\":1000,\"memberId\":" + other.getId() + "}").andExpect(status().isCreated());

        assertThat(paymentsOf(member)).hasSize(1);
        assertThat(paymentsOf(other)).isEmpty();
    }

    @Test
    void pinSession_canReport() throws Exception {
        report(testSessions.pinSession(MemberRoleEnum.MEMBER), "{\"amountCents\":1000}").andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"amountCents\":null}", "{\"amountCents\":0}", "{\"amountCents\":-100}",
            "{\"amountCents\":500,\"reference\":\"LONG_REFERENCE\"}"})
    void invalidReport_returns400With103AndSavesNothing(String body) throws Exception {
        report(memberSession, body.replace("LONG_REFERENCE", "x".repeat(141)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));

        assertThat(paymentsOf(member)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"amountCents\":\"palju\"}", "{\"amountCents\":12.5}", "not json"})
    void unreadableReport_returns400With104(String body) throws Exception {
        report(memberSession, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));

        assertThat(paymentsOf(member)).isEmpty();
    }

    // ---- own payments ----

    @Test
    void myPayments_defaultToPendingNewestFirst() throws Exception {
        report(memberSession, "{\"amountCents\":100,\"reference\":\"esimene\"}");
        report(memberSession, "{\"amountCents\":200,\"reference\":\"teine\"}");
        decidedPayment(member, PaymentStatusEnum.CONFIRMED, null);

        mockMvc.perform(get("/api/me/payments").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reference").value("teine"))
                .andExpect(jsonPath("$[1].reference").value("esimene"));
    }

    @Test
    void myPayments_byStatus_rejectedCarriesTheAdminsNote() throws Exception {
        report(memberSession, "{\"amountCents\":100}");
        decidedPayment(member, PaymentStatusEnum.CONFIRMED, null);
        decidedPayment(member, PaymentStatusEnum.REJECTED, "Laekumist ei leitud");

        myPayments(memberSession, "CONFIRMED")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].decidedAt").isNotEmpty());
        myPayments(memberSession, "REJECTED")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].decisionNote").value("Laekumist ei leitud"));
    }

    @Test
    void myPayments_neverShowAnotherMembersPayments() throws Exception {
        Member other = testSessions.createMember(MemberRoleEnum.MEMBER);
        MockHttpSession otherSession = testSessions.passwordSession(other);
        report(otherSession, "{\"amountCents\":900}");

        myPayments(memberSession, "PENDING").andExpect(jsonPath("$.length()").value(0));
        myPayments(otherSession, "PENDING").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void myPayments_showAtMost50() throws Exception {
        for (int n = 0; n <= PaymentService.MAX_LISTED_PAYMENTS; n++) {
            report(memberSession, "{\"amountCents\":" + (n + 1) + "}");
        }

        myPayments(memberSession, "PENDING")
                .andExpect(jsonPath("$.length()").value(PaymentService.MAX_LISTED_PAYMENTS))
                .andExpect(jsonPath("$[0].amountCents").value(PaymentService.MAX_LISTED_PAYMENTS + 1));
    }

    @Test
    void unknownStatus_returns400With104() throws Exception {
        myPayments(memberSession, "CARD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    // ---- who ----

    @Test
    void withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/payments/details")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":100}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutCsrfToken_returns403AndSavesNothing() throws Exception {
        mockMvc.perform(post("/api/payments").session(memberSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountCents\":100}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));

        assertThat(paymentsOf(member)).isEmpty();
    }

    // ---- helpers ----

    private ResultActions report(MockHttpSession session, String body) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/payments").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
        forget();
        return result;
    }

    private ResultActions myPayments(MockHttpSession session, String status) throws Exception {
        return mockMvc.perform(get("/api/me/payments").param("status", status).session(session));
    }

    // As B16 will leave it; written directly because deciding is not part of this task
    private void decidedPayment(Member someone, PaymentStatusEnum status, String note) {
        Payment payment = new Payment();
        payment.setMember(entityManager.getReference(Member.class, someone.getId()));
        payment.setAmountCents(700L);
        payment.setMethod(PaymentMethodEnum.BANK_TRANSFER);
        payment.setStatus(status);
        payment.setReportedAt(OffsetDateTime.now(clock).minusDays(2));
        payment.setDecidedAt(OffsetDateTime.now(clock).minusDays(1));
        payment.setDecisionNote(note);
        entityManager.persist(payment);
        forget();
    }

    private List<Payment> paymentsOf(Member someone) {
        forget();
        return entityManager.createQuery("select p from Payment p where p.member.id = :id", Payment.class)
                .setParameter("id", someone.getId()).getResultList();
    }

    private long balanceOf(Member someone) {
        forget();
        return entityManager.find(Member.class, someone.getId()).getBalanceCents();
    }

    private long ledgerRowsOf(Member someone) {
        return entityManager.createQuery("select count(e) from AccountEntry e where e.member.id = :id", Long.class)
                .setParameter("id", someone.getId()).getSingleResult();
    }

    // All requests share the test transaction: write and forget, as a commit would between real requests
    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
