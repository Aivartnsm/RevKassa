package ee.revkassa.controller.admin.payment;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.payment.Payment;
import ee.revkassa.persistence.payment.PaymentStatusEnum;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B16: the admin's payment queue, confirming, rejecting and entering a confirmed payment, through the real security
 * chain and PostgreSQL. A confirmed payment must have exactly one PAYMENT ledger row; a rejected one none.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminPaymentFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private EntityManager entityManager;

    private Member member;
    private MockHttpSession memberSession;
    private Member admin;
    private MockHttpSession adminSession;

    @BeforeEach
    void createMemberAndAdmin() throws Exception {
        member = testSessions.createMember(MemberRoleEnum.MEMBER);
        member.setDisplayName("Maksja Liige");
        memberSession = testSessions.passwordSession(member);
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        admin.setDisplayName("Kinnitaja Admin");
        adminSession = testSessions.passwordSession(admin);
        forget();
    }

    // ---- list ----

    @Test
    void pendingList_isOldestFirstWithTheMembersName() throws Exception {
        long first = report(2000, "esimene");
        long second = report(1000, "teine");

        List<Integer> ids = pendingIdsOfThisTest();

        assertThat(ids).containsSubsequence(Math.toIntExact(first), Math.toIntExact(second));
        send(get("/api/admin/payments"), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.paymentId == " + first + ")].memberName").value("Maksja Liige"))
                .andExpect(jsonPath("$[?(@.paymentId == " + first + ")].memberId").value(Math.toIntExact(member.getId())))
                .andExpect(jsonPath("$[?(@.paymentId == " + first + ")].amountCents").value(2000))
                .andExpect(jsonPath("$[?(@.paymentId == " + first + ")].reference").value("esimene"));
    }

    @Test
    void decidedLists_showWhoDecidedLatestFirst() throws Exception {
        long confirmed = report(500, null);
        long rejected = report(700, null);
        send(post("/api/admin/payments/" + confirmed + "/confirm"), adminSession);
        send(post("/api/admin/payments/" + rejected + "/reject"), adminSession);

        send(get("/api/admin/payments").param("status", "CONFIRMED"), adminSession)
                .andExpect(jsonPath("$[0].paymentId").value(Math.toIntExact(confirmed)))
                .andExpect(jsonPath("$[0].decidedByName").value("Kinnitaja Admin"));
        send(get("/api/admin/payments").param("status", "REJECTED"), adminSession)
                .andExpect(jsonPath("$[0].paymentId").value(Math.toIntExact(rejected)));
    }

    @Test
    void unknownStatus_returns400() throws Exception {
        send(get("/api/admin/payments").param("status", "CARD"), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    // ---- confirm ----

    @Test
    void confirm_writesExactlyOnePaymentRowAndRaisesTheBalance() throws Exception {
        long balanceBefore = balanceOf(member);
        long paymentId = report(2500, "oktoober");

        send(post("/api/admin/payments/" + paymentId + "/confirm"), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.decidedByName").value("Kinnitaja Admin"));

        Payment payment = entityManager.find(Payment.class, paymentId);
        assertThat(payment.getDecidedBy().getId()).isEqualTo(admin.getId());
        assertThat(ledgerRowsOf(paymentId)).singleElement().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(AccountEntryTypeEnum.PAYMENT);
            assertThat(entry.getAmountCents()).isEqualTo(2500);
            assertThat(entry.getMember().getId()).isEqualTo(member.getId());
            assertThat(entry.getCreatedBy().getId()).isEqualTo(admin.getId());
        });
        assertThat(balanceOf(member)).isEqualTo(balanceBefore + 2500).isEqualTo(ledgerSumOf(member));
    }

    @Test
    void confirmedPayment_showsInTheMembersHistoryAndList() throws Exception {
        long paymentId = report(2500, null);
        send(post("/api/admin/payments/" + paymentId + "/confirm"), adminSession);

        send(get("/api/me/entries"), memberSession)
                .andExpect(jsonPath("$.entries[0].description").value("Makse · pangaülekanne"))
                .andExpect(jsonPath("$.entries[0].amountCents").value(2500));
        send(get("/api/me/payments").param("status", "CONFIRMED"), memberSession)
                .andExpect(jsonPath("$[0].paymentId").value(Math.toIntExact(paymentId)));
        send(get("/api/me/payments"), memberSession).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void confirmTwice_returns403With132AndKeepsOneRow() throws Exception {
        long paymentId = report(1000, null);
        send(post("/api/admin/payments/" + paymentId + "/confirm"), adminSession).andExpect(status().isOk());

        send(post("/api/admin/payments/" + paymentId + "/confirm"), adminSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(132));

        assertThat(ledgerRowsOf(paymentId)).hasSize(1);
        assertThat(balanceOf(member)).isEqualTo(ledgerSumOf(member));
    }

    @Test
    void confirmAfterReject_returns403With132AndWritesNoRow() throws Exception {
        long paymentId = report(1000, null);
        send(post("/api/admin/payments/" + paymentId + "/reject"), adminSession).andExpect(status().isOk());

        send(post("/api/admin/payments/" + paymentId + "/confirm"), adminSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(132));

        assertThat(ledgerRowsOf(paymentId)).isEmpty();
        assertThat(entityManager.find(Payment.class, paymentId).getStatus()).isEqualTo(PaymentStatusEnum.REJECTED);
    }

    @Test
    void unknownPayment_returns404() throws Exception {
        send(post("/api/admin/payments/" + Long.MAX_VALUE + "/confirm"), adminSession)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
        send(post("/api/admin/payments/" + Long.MAX_VALUE + "/reject"), adminSession)
                .andExpect(status().isNotFound());
    }

    // ---- reject ----

    @Test
    void reject_withNote_leavesBalanceAndLedgerAndTheMemberSeesTheNote() throws Exception {
        long balanceBefore = balanceOf(member);
        long paymentId = report(1500, null);

        sendJson(post("/api/admin/payments/" + paymentId + "/reject"), adminSession,
                "{\"note\":\"  Laekumist ei leitud \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Laekumist ei leitud"));

        assertThat(ledgerRowsOf(paymentId)).isEmpty();
        assertThat(balanceOf(member)).isEqualTo(balanceBefore);
        send(get("/api/me/payments").param("status", "REJECTED"), memberSession)
                .andExpect(jsonPath("$[0].decisionNote").value("Laekumist ei leitud"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"note\":null}", "{\"note\":\"   \"}"})
    void reject_withoutNote_savesNoNote(String body) throws Exception {
        long paymentId = report(1500, null);

        sendJson(post("/api/admin/payments/" + paymentId + "/reject"), adminSession, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionNote").value(nullValue()));
    }

    @Test
    void rejectWithTooLongNote_returns400AndStaysPending() throws Exception {
        long paymentId = report(1500, null);

        sendJson(post("/api/admin/payments/" + paymentId + "/reject"), adminSession,
                "{\"note\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));

        assertThat(entityManager.find(Payment.class, paymentId).getStatus()).isEqualTo(PaymentStatusEnum.PENDING);
    }

    // ---- admin enters a payment ----

    @Test
    void adminEnteredPayment_isConfirmedAtOnceWithExactlyOneRow() throws Exception {
        long balanceBefore = balanceOf(member);

        long paymentId = enter("{\"memberId\":" + member.getId() + ",\"amountCents\":3000,\"reference\":\" LHV 12345 \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.reference").value("LHV 12345"))
                .andExpect(jsonPath("$.memberName").value("Maksja Liige"))
                .andExpect(jsonPath("$.decidedByName").value("Kinnitaja Admin"))
                .andReturn().getResponse().getContentAsString().transform(AdminPaymentFlowTest::paymentIdOf);

        assertThat(entityManager.find(Payment.class, paymentId).getDecidedBy().getId()).isEqualTo(admin.getId());
        assertThat(ledgerRowsOf(paymentId)).singleElement()
                .satisfies(entry -> assertThat(entry.getAmountCents()).isEqualTo(3000));
        assertThat(balanceOf(member)).isEqualTo(balanceBefore + 3000).isEqualTo(ledgerSumOf(member));
    }

    @Test
    void adminEnteredPayment_worksForADeactivatedMember() throws Exception {
        entityManager.find(Member.class, member.getId()).setActive(false);
        forget();

        enter("{\"memberId\":" + member.getId() + ",\"amountCents\":3000}").andExpect(status().isCreated());

        assertThat(balanceOf(member)).isEqualTo(ledgerSumOf(member));
    }

    @Test
    void adminEnteredPayment_forUnknownMember_returns404() throws Exception {
        enter("{\"memberId\":" + Long.MAX_VALUE + ",\"amountCents\":3000}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"amountCents\":3000}", "{\"memberId\":MEMBER}", "{\"memberId\":MEMBER,\"amountCents\":0}",
            "{\"memberId\":MEMBER,\"amountCents\":-5}", "{\"memberId\":MEMBER,\"amountCents\":5,\"reference\":\"LONG\"}"})
    void invalidAdminEnteredPayment_returns400AndWritesNothing(String body) throws Exception {
        enter(body.replace("MEMBER", member.getId().toString()).replace("LONG", "x".repeat(141)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));

        assertThat(ledgerSumOf(member)).isZero();
    }

    // ---- who ----

    @Test
    void member_gets403Everywhere() throws Exception {
        long paymentId = report(1000, null);

        send(get("/api/admin/payments"), memberSession).andExpect(status().isForbidden());
        send(post("/api/admin/payments/" + paymentId + "/confirm"), memberSession).andExpect(status().isForbidden());
        send(post("/api/admin/payments/" + paymentId + "/reject"), memberSession).andExpect(status().isForbidden());
        sendJson(post("/api/admin/payments"), memberSession, "{\"memberId\":" + member.getId() + ",\"amountCents\":9999}")
                .andExpect(status().isForbidden());

        assertThat(ledgerSumOf(member)).isZero();
        assertThat(entityManager.find(Payment.class, paymentId).getStatus()).isEqualTo(PaymentStatusEnum.PENDING);
    }

    @Test
    void adminWithPinSession_gets403() throws Exception {
        long paymentId = report(1000, null);

        send(post("/api/admin/payments/" + paymentId + "/confirm"), testSessions.pinSession(MemberRoleEnum.ADMIN))
                .andExpect(status().isForbidden());

        assertThat(ledgerRowsOf(paymentId)).isEmpty();
    }

    @Test
    void withoutCsrfToken_returns403AndConfirmsNothing() throws Exception {
        long paymentId = report(1000, null);

        mockMvc.perform(post("/api/admin/payments/" + paymentId + "/confirm").session(adminSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));

        assertThat(ledgerRowsOf(paymentId)).isEmpty();
    }

    @Test
    void withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/payments")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    // Through the member's own endpoint, as in real use
    private long report(long amountCents, String reference) throws Exception {
        String body = "{\"amountCents\":" + amountCents + (reference == null ? "" : ",\"reference\":\"" + reference + "\"") + "}";
        return sendJson(post("/api/payments"), memberSession, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(AdminPaymentFlowTest::paymentIdOf);
    }

    private ResultActions enter(String body) throws Exception {
        return sendJson(post("/api/admin/payments"), adminSession, body);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        ResultActions result = mockMvc.perform(request.with(csrf()).session(session));
        forget();
        return result;
    }

    private ResultActions sendJson(MockHttpServletRequestBuilder request, MockHttpSession session, String body)
            throws Exception {
        return send(request.contentType(MediaType.APPLICATION_JSON).content(body), session);
    }

    // The whole queue may hold other pending payments (dev data); only this test's member's ones, in list order
    private List<Integer> pendingIdsOfThisTest() throws Exception {
        String json = send(get("/api/admin/payments"), adminSession).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[?(@.memberId == " + member.getId() + ")].paymentId");
    }

    private List<AccountEntry> ledgerRowsOf(long paymentId) {
        return entityManager.createQuery("select e from AccountEntry e where e.payment.id = :id", AccountEntry.class)
                .setParameter("id", paymentId).getResultList();
    }

    private long balanceOf(Member someone) {
        forget();
        return entityManager.find(Member.class, someone.getId()).getBalanceCents();
    }

    private long ledgerSumOf(Member someone) {
        return entityManager.createQuery(
                        "select coalesce(sum(e.amountCents), 0) from AccountEntry e where e.member.id = :id", Long.class)
                .setParameter("id", someone.getId()).getSingleResult();
    }

    // All requests share the test transaction: write and forget, as a commit would between real requests
    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }

    private static long paymentIdOf(String json) {
        return ((Number) JsonPath.read(json, "$.paymentId")).longValue();
    }
}
