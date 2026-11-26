package ee.revkassa.controller.admin.membershipfee;

import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershipcharge.MembershipCharge;
import ee.revkassa.persistence.membershipcharge.MembershipChargeStatusEnum;
import ee.revkassa.persistence.membershipfee.MembershipFee;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.service.MembershipChargeService;
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

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B24: a month's fee list and waiving a fee, through the real security chain and PostgreSQL. Charges are made for a
 * month in 2099 with the real charging code, for this test's members only, so the list and totals are exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminMembershipChargeFlowTest {

    private static final YearMonth MONTH = YearMonth.of(2099, 10);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private MembershipChargeService membershipChargeService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    private Member admin;
    private MockHttpSession adminSession;
    private MembershipLevel level;
    private Member anna;
    private Member boris;

    @BeforeEach
    void chargeTwoMembers() throws Exception {
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        admin.setDisplayName("Tühistaja Admin");
        adminSession = testSessions.passwordSession(admin);
        level = level();
        fee(level, MONTH, 3540);
        anna = memberOn(level, "Anna Aas");
        boris = memberOn(level, "Boris Bõõs");
        membershipChargeService.chargeMember(anna.getId(), MONTH);
        membershipChargeService.chargeMember(boris.getId(), MONTH);
        forget();
    }

    // ---- list ----

    @Test
    void list_showsTheMonthsFeesByNameWithTotals() throws Exception {
        waive(chargeOf(boris), "{\"note\":\"Juhatuse otsus\"}");

        listMonth()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2099-10"))
                .andExpect(jsonPath("$.charges.length()").value(2))
                .andExpect(jsonPath("$.charges[0].memberName").value("Anna Aas"))
                .andExpect(jsonPath("$.charges[0].memberId").value(Math.toIntExact(anna.getId())))
                .andExpect(jsonPath("$.charges[0].levelName").value(level.getName()))
                .andExpect(jsonPath("$.charges[0].amountCents").value(3540))
                .andExpect(jsonPath("$.charges[0].status").value("CHARGED"))
                .andExpect(jsonPath("$.charges[0].waivedAt").value(nullValue()))
                .andExpect(jsonPath("$.charges[1].memberName").value("Boris Bõõs"))
                .andExpect(jsonPath("$.charges[1].status").value("WAIVED"))
                .andExpect(jsonPath("$.charges[1].waivedByName").value("Tühistaja Admin"))
                .andExpect(jsonPath("$.charges[1].waiveNote").value("Juhatuse otsus"))
                .andExpect(jsonPath("$.chargedCount").value(1))
                .andExpect(jsonPath("$.chargedTotalCents").value(3540))
                .andExpect(jsonPath("$.waivedCount").value(1))
                .andExpect(jsonPath("$.waivedTotalCents").value(3540));
    }

    @Test
    void list_withoutMonth_showsTheCurrentMonth() throws Exception {
        send(get("/api/admin/fees/charges"), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(YearMonth.now(clock).toString()));
    }

    @Test
    void list_monthWithoutCharges_isEmpty() throws Exception {
        send(get("/api/admin/fees/charges").param("month", "2099-12"), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges.length()").value(0))
                .andExpect(jsonPath("$.chargedCount").value(0))
                .andExpect(jsonPath("$.chargedTotalCents").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2099-13", "oktoober", "2099-10-01"})
    void list_malformedMonth_returns400(String month) throws Exception {
        send(get("/api/admin/fees/charges").param("month", month), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    @Test
    void list_byMember_returns403() throws Exception {
        send(get("/api/admin/fees/charges").param("month", MONTH.toString()), testSessions.memberSession())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- waive ----

    @Test
    void waive_marksTheFeeWaivedAndGivesTheWholeAmountBack() throws Exception {
        long chargeId = chargeOf(anna);

        waive(chargeId, "{\"note\":\"  Haiguse tõttu  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeId").value(Math.toIntExact(chargeId)))
                .andExpect(jsonPath("$.status").value("WAIVED"))
                .andExpect(jsonPath("$.waivedAt").isNotEmpty())
                .andExpect(jsonPath("$.waivedByName").value("Tühistaja Admin"))
                .andExpect(jsonPath("$.waiveNote").value("Haiguse tõttu"));

        MembershipCharge charge = entityManager.find(MembershipCharge.class, chargeId);
        assertThat(charge.getStatus()).isEqualTo(MembershipChargeStatusEnum.WAIVED);
        assertThat(charge.getWaivedBy().getId()).isEqualTo(admin.getId());
        assertThat(entriesOf(anna)).extracting(AccountEntry::getType, AccountEntry::getAmountCents)
                .containsExactly(tuple(AccountEntryTypeEnum.MEMBERSHIP_FEE, -3540L),
                        tuple(AccountEntryTypeEnum.MEMBERSHIP_FEE_WAIVER, 3540L));
        assertThat(entriesOf(anna).getLast()).satisfies(entry -> {
            assertThat(entry.getNote()).isEqualTo("Haiguse tõttu");
            assertThat(entry.getCreatedBy().getId()).isEqualTo(admin.getId());
            assertThat(entry.getMembershipCharge().getId()).isEqualTo(chargeId);
        });
        assertBalance(anna, 0);
    }

    @Test
    void waive_showsInTheMembersHistoryWithMonthAndNote() throws Exception {
        MockHttpSession annaSession = testSessions.passwordSession(anna);
        waive(chargeOf(anna), "{\"note\":\"Juhatuse otsus\"}");

        send(get("/api/me/entries"), annaSession)
                .andExpect(jsonPath("$.entries[0].description").value("Liikmemaksu tühistus · oktoober 2099 · Juhatuse otsus"))
                .andExpect(jsonPath("$.entries[0].amountCents").value(3540))
                .andExpect(jsonPath("$.entries[1].description").value("Liikmemaks · oktoober 2099"));
    }

    @Test
    void waive_twice_returns403AndGivesBackOnlyOnce() throws Exception {
        long chargeId = chargeOf(anna);
        waive(chargeId, "{\"note\":\"esimene\"}");

        waive(chargeId, "{\"note\":\"teine\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(141));

        assertThat(entriesOf(anna)).hasSize(2);
        assertThat(entityManager.find(MembershipCharge.class, chargeId).getWaiveNote()).isEqualTo("esimene");
        assertBalance(anna, 0);
    }

    @Test
    void waivedMonth_isNeverChargedAgain() throws Exception {
        waive(chargeOf(anna), "{\"note\":\"vabastus\"}");

        assertThat(membershipChargeService.chargeMember(anna.getId(), MONTH)).isFalse();
        membershipChargeService.chargeMonth(MONTH);

        assertThat(entriesOf(anna)).hasSize(2);
        assertBalance(anna, 0);
    }

    @Test
    void waive_inactiveMember_isAllowed() throws Exception {
        entityManager.find(Member.class, anna.getId()).setActive(false);
        forget();

        waive(chargeOf(anna), "{\"note\":\"lahkus\"}").andExpect(status().isOk());

        assertBalance(anna, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"note\":\"   \"}", "{\"note\":null}"})
    void waive_withoutNote_returns400AndChangesNothing(String body) throws Exception {
        long chargeId = chargeOf(anna);

        waive(chargeId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));

        assertThat(entityManager.find(MembershipCharge.class, chargeId).getStatus()).isEqualTo(MembershipChargeStatusEnum.CHARGED);
        assertBalance(anna, -3540);
    }

    @Test
    void waive_noteTooLong_returns400() throws Exception {
        waive(chargeOf(anna), "{\"note\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void waive_unknownCharge_returns404() throws Exception {
        waive(Long.MAX_VALUE, "{\"note\":\"keegi\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    void waive_malformedId_returns400() throws Exception {
        sendJson(post("/api/admin/fees/charges/abc/waive"), adminSession, "{\"note\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    @Test
    void waive_byTheMemberThemselves_returns403AndChangesNothing() throws Exception {
        MockHttpSession annaSession = testSessions.passwordSession(anna);

        sendJson(post("/api/admin/fees/charges/" + chargeOf(anna) + "/waive"), annaSession, "{\"note\":\"tahan\"}")
                .andExpect(status().isForbidden());

        assertBalance(anna, -3540);
    }

    // The unique index is the last line of defence if the code ever let a second waiver through
    @Test
    void database_refusesASecondWaiverRowForTheSameFee() throws Exception {
        long chargeId = chargeOf(anna);
        waive(chargeId, "{\"note\":\"esimene\"}");

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        insert into account_entry (member_id, type, amount_cents, membership_charge_id, note, created_at)
                        values (:memberId, 'MEMBERSHIP_FEE_WAIVER', 3540, :chargeId, 'teine', now())""")
                .setParameter("memberId", anna.getId())
                .setParameter("chargeId", chargeId)
                .executeUpdate())
                .hasMessageContaining("ux_account_entry_membership_charge_id_type");
    }

    // ---- helpers ----

    private ResultActions listMonth() throws Exception {
        return send(get("/api/admin/fees/charges").param("month", MONTH.toString()), adminSession);
    }

    private ResultActions waive(long chargeId, String body) throws Exception {
        return sendJson(post("/api/admin/fees/charges/" + chargeId + "/waive"), adminSession, body);
    }

    private long chargeOf(Member member) {
        return entityManager.createQuery(
                        "select c.id from MembershipCharge c where c.member.id = :id and c.month = :month", Long.class)
                .setParameter("id", member.getId())
                .setParameter("month", MONTH.atDay(1))
                .getSingleResult();
    }

    private Member memberOn(MembershipLevel memberLevel, String name) {
        Member member = testSessions.createMember(MemberRoleEnum.MEMBER);
        member.setDisplayName(name);
        member.setLevel(memberLevel);
        entityManager.flush();
        return member;
    }

    private MembershipLevel level() {
        MembershipLevel newLevel = new MembershipLevel();
        newLevel.setName("Tühistustase " + UUID.randomUUID());
        newLevel.setSortOrder(100);
        newLevel.setActive(true);
        newLevel.setCreatedAt(OffsetDateTime.now());
        entityManager.persist(newLevel);
        return newLevel;
    }

    private void fee(MembershipLevel feeLevel, YearMonth validFrom, long amountCents) {
        MembershipFee fee = new MembershipFee();
        fee.setLevel(feeLevel);
        fee.setValidFrom(validFrom.atDay(1));
        fee.setAmountCents(amountCents);
        fee.setCreatedBy(admin);
        fee.setCreatedAt(OffsetDateTime.now());
        entityManager.persist(fee);
        entityManager.flush();
    }

    private List<AccountEntry> entriesOf(Member member) {
        return entityManager.createQuery("""
                        select e from AccountEntry e left join fetch e.createdBy left join fetch e.membershipCharge
                        where e.member.id = :id order by e.id""", AccountEntry.class)
                .setParameter("id", member.getId()).getResultList();
    }

    private void assertBalance(Member member, long expectedCents) {
        forget();
        long balance = entityManager.find(Member.class, member.getId()).getBalanceCents();
        long ledgerSum = entityManager.createQuery(
                        "select coalesce(sum(e.amountCents), 0) from AccountEntry e where e.member.id = :id", Long.class)
                .setParameter("id", member.getId()).getSingleResult();
        assertThat(balance).isEqualTo(expectedCents).isEqualTo(ledgerSum);
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

    // All requests share the test transaction: write and forget, as a commit would between real requests
    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }
}
