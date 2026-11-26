package ee.revkassa.controller.admin.member;

import com.jayway.jsonpath.JsonPath;
import ee.revkassa.infrastructure.mail.MailMessage;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.membershiplevel.MembershipLevel;
import ee.revkassa.persistence.membershiplevel.MembershipLevelRepository;
import ee.revkassa.persistence.passwordtoken.PasswordToken;
import ee.revkassa.persistence.passwordtoken.PasswordTokenPurposeEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import ee.revkassa.service.PurchaseService;
import ee.revkassa.testdata.FakeMailer;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B17: the admin's member management (list, create with invite and PIN, edit, PIN reset, balance corrections and the
 * member's purchases) through the real security chain, PostgreSQL and a fake Mailer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class, FakeMailer.Config.class})
@ExtendWith(OutputCaptureExtension.class)
class AdminMemberFlowTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;
    @Autowired
    private TestData testData;
    @Autowired
    private FakeMailer fakeMailer;
    @Autowired
    private PinService pinService;
    @Autowired
    private MembershipLevelRepository membershipLevelRepository;
    @Autowired
    private EntityManager entityManager;

    private Member member;
    private MockHttpSession memberSession;
    private Member admin;
    private MockHttpSession adminSession;
    private String newEmail;

    @BeforeEach
    void createMemberAndAdmin() throws Exception {
        member = testSessions.createMember(MemberRoleEnum.MEMBER);
        member.setDisplayName("Hallatav Liige");
        memberSession = testSessions.passwordSession(member);
        admin = testSessions.createMember(MemberRoleEnum.ADMIN);
        admin.setDisplayName("Haldaja Admin");
        adminSession = testSessions.passwordSession(admin);
        newEmail = "uus-liige-" + COUNTER.incrementAndGet() + "-" + UUID.randomUUID() + "@example.org";
        fakeMailer.clear();
        forget();
    }

    // ---- list ----

    @Test
    void list_showsMembersWithBalanceAndWhetherPasswordIsSet() throws Exception {
        Member withoutPassword = testData.member("no-password-" + UUID.randomUUID() + "@example.org", "b17-hmac-" + UUID.randomUUID());
        forget();

        send(get("/api/admin/members"), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath(memberPath(member) + ".displayName").value("Hallatav Liige"))
                .andExpect(jsonPath(memberPath(member) + ".email").value(member.getEmail()))
                .andExpect(jsonPath(memberPath(member) + ".role").value("MEMBER"))
                .andExpect(jsonPath(memberPath(member) + ".active").value(true))
                .andExpect(jsonPath(memberPath(member) + ".balanceCents").value(0))
                .andExpect(jsonPath(memberPath(member) + ".passwordSet").value(true))
                .andExpect(jsonPath(memberPath(member) + ".levelId").value(Math.toIntExact(member.getLevel().getId())))
                .andExpect(jsonPath(memberPath(member) + ".levelName").value(member.getLevel().getName()))
                .andExpect(jsonPath(memberPath(withoutPassword) + ".passwordSet").value(false));
    }

    @Test
    void list_neverContainsPinOrPasswordHash() throws Exception {
        String json = send(get("/api/admin/members"), adminSession).andReturn().getResponse().getContentAsString();

        assertThat(json).doesNotContain("pinHmac", "passwordHash", member.getPinHmac());
    }

    @Test
    void list_byMember_returns403() throws Exception {
        send(get("/api/admin/members"), memberSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    @Test
    void list_byAdminWithPin_returns403() throws Exception {
        send(get("/api/admin/members"), testSessions.pinSession(MemberRoleEnum.ADMIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
    }

    // ---- create ----

    @Test
    void create_returnsNewPinOnceAndStoresOnlyItsHmac() throws Exception {
        String json = create("  Uus Liige ", newEmail, "MEMBER", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pin").value(matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$.member.displayName").value("Uus Liige"))
                .andExpect(jsonPath("$.member.email").value(newEmail))
                .andExpect(jsonPath("$.member.role").value("MEMBER"))
                .andExpect(jsonPath("$.member.active").value(true))
                .andExpect(jsonPath("$.member.feeExempt").value(true))
                .andExpect(jsonPath("$.member.balanceCents").value(0))
                .andExpect(jsonPath("$.member.passwordSet").value(false))
                .andReturn().getResponse().getContentAsString();

        Member created = entityManager.find(Member.class, idOf(json, "$.member.memberId"));
        String pin = JsonPath.read(json, "$.pin");
        assertThat(created.getPinHmac()).isEqualTo(pinService.hash(pin)).doesNotContain(pin);
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getLevel().getId()).isEqualTo(firstActiveLevel().getId());
    }

    @Test
    void create_withLevel_assignsThatLevel() throws Exception {
        MembershipLevel junior = level("Noorem", true);

        String json = sendJson(post("/api/admin/members"), adminSession,
                        "{\"displayName\":\"Tasemega\",\"email\":\"" + newEmail + "\",\"role\":\"MEMBER\",\"feeExempt\":false,"
                                + "\"levelId\":" + junior.getId() + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.levelId").value(Math.toIntExact(junior.getId())))
                .andExpect(jsonPath("$.member.levelName").value(junior.getName()))
                .andReturn().getResponse().getContentAsString();

        assertThat(entityManager.find(Member.class, idOf(json, "$.member.memberId")).getLevel().getId())
                .isEqualTo(junior.getId());
    }

    @Test
    void create_withInactiveLevel_returns403AndSendsNothing() throws Exception {
        MembershipLevel retired = level("Mahavõetud", false);

        sendJson(post("/api/admin/members"), adminSession,
                "{\"displayName\":\"Nimi\",\"email\":\"" + newEmail + "\",\"role\":\"MEMBER\",\"feeExempt\":false,"
                        + "\"levelId\":" + retired.getId() + "}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(138));

        assertThat(fakeMailer.sent()).isEmpty();
    }

    @Test
    void create_withUnknownLevel_returns404() throws Exception {
        sendJson(post("/api/admin/members"), adminSession,
                "{\"displayName\":\"Nimi\",\"email\":\"" + newEmail + "\",\"role\":\"MEMBER\",\"feeExempt\":false,"
                        + "\"levelId\":" + Long.MAX_VALUE + "}")
                .andExpect(status().isNotFound());
    }

    @Test
    void create_emailsInviteLinkToTheNewMember() throws Exception {
        String json = create("Kutsutav", newEmail, "MEMBER", false).andReturn().getResponse().getContentAsString();

        MailMessage email = fakeMailer.last();
        assertThat(email.to()).isEqualTo(newEmail);
        assertThat(email.subject()).isEqualTo("RevKassa: määra oma parool");
        assertThat(email.body()).contains("Tere, Kutsutav!").doesNotContain((String) JsonPath.read(json, "$.pin"));
        assertThat(openLinksOf(idOf(json, "$.member.memberId"))).singleElement().satisfies(token -> {
            assertThat(token.getPurpose()).isEqualTo(PasswordTokenPurposeEnum.INVITE);
            assertThat(token.getCreatedBy().getId()).isEqualTo(admin.getId());
        });
    }

    @Test
    void create_doesNotLogThePin(CapturedOutput output) throws Exception {
        String json = create("Logimata", newEmail, "MEMBER", false).andReturn().getResponse().getContentAsString();

        assertThat(output.getOut()).contains("Member created").doesNotContain((String) JsonPath.read(json, "$.pin"));
    }

    @Test
    void create_asAdmin_createsAnAdmin() throws Exception {
        create("Uus Admin", newEmail, "ADMIN", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.role").value("ADMIN"));
    }

    @Test
    void create_emailTakenInOtherCase_returns403AndSendsNothing() throws Exception {
        create("Teine", member.getEmail().toUpperCase(), "MEMBER", false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(133));

        assertThat(fakeMailer.sent()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"displayName\":\"Nimi\",\"role\":\"MEMBER\",\"feeExempt\":false}",
            "{\"displayName\":\"Nimi\",\"email\":\"  \",\"role\":\"MEMBER\",\"feeExempt\":false}",
            "{\"displayName\":\"Nimi\",\"email\":\"pole-e-post\",\"role\":\"MEMBER\",\"feeExempt\":false}",
            "{\"displayName\":\" \",\"email\":\"a@example.org\",\"role\":\"MEMBER\",\"feeExempt\":false}",
            "{\"displayName\":\"Nimi\",\"email\":\"a@example.org\",\"feeExempt\":false}",
            "{\"displayName\":\"Nimi\",\"email\":\"a@example.org\",\"role\":\"MEMBER\"}"
    })
    void create_invalidFields_returns400(String body) throws Exception {
        sendJson(post("/api/admin/members"), adminSession, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));
    }

    @Test
    void create_unknownRole_returns400() throws Exception {
        create("Nimi", newEmail, "OWNER", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(104));
    }

    // That nothing is saved needs a real commit: PasswordLinkRollbackTest
    @Test
    void create_mailServerFails_returns503() throws Exception {
        fakeMailer.failNextSend();

        create("Kirjata", newEmail, "MEMBER", false)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(123));
    }

    @Test
    void create_byMember_returns403AndCreatesNothing() throws Exception {
        sendJson(post("/api/admin/members"), memberSession, createBody("Kuri", newEmail, "ADMIN", false))
                .andExpect(status().isForbidden());

        assertThat(memberCountWithEmail(newEmail)).isZero();
    }

    // ---- update ----

    @Test
    void update_changesFieldsAndVersion() throws Exception {
        update(member.getId(), " Uus Nimi ", newEmail, "MEMBER", true, true, versionOf(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Uus Nimi"))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.feeExempt").value(true))
                .andExpect(jsonPath("$.version").value(versionOf(member)));

        Member saved = entityManager.find(Member.class, member.getId());
        assertThat(saved.getDisplayName()).isEqualTo("Uus Nimi");
        assertThat(saved.getFeeExempt()).isTrue();
    }

    @Test
    void update_changesLevel() throws Exception {
        MembershipLevel junior = level("Noorem", true);

        update(member.getId(), "Nimi", member.getEmail(), "MEMBER", true, false, versionOf(member), junior.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levelId").value(Math.toIntExact(junior.getId())))
                .andExpect(jsonPath("$.levelName").value(junior.getName()));

        assertThat(entityManager.find(Member.class, member.getId()).getLevel().getId()).isEqualTo(junior.getId());
    }

    @Test
    void update_toInactiveLevel_returns403() throws Exception {
        MembershipLevel retired = level("Mahavõetud", false);

        update(member.getId(), "Nimi", member.getEmail(), "MEMBER", true, false, versionOf(member), retired.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(138));
    }

    @Test
    void update_memberOnDeactivatedLevel_canStillBeEditedAndKeepsIt() throws Exception {
        MembershipLevel retired = level("Mahavõetud", false);
        entityManager.find(Member.class, member.getId()).setLevel(entityManager.find(MembershipLevel.class, retired.getId()));
        forget();

        update(member.getId(), "Uus Nimi", member.getEmail(), "MEMBER", true, false, versionOf(member), retired.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levelId").value(Math.toIntExact(retired.getId())));
    }

    @Test
    void update_withoutLevel_returns400() throws Exception {
        sendJson(put("/api/admin/members/" + member.getId()), adminSession,
                "{\"displayName\":\"Nimi\",\"email\":\"" + member.getEmail() + "\",\"role\":\"MEMBER\",\"active\":true,"
                        + "\"feeExempt\":false,\"version\":" + versionOf(member) + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.levelId").exists());
    }

    @Test
    void update_doesNotTouchBalance() throws Exception {
        adjust(member.getId(), "{\"amountCents\":-1500,\"note\":\"vana võlg\"}");
        long versionBeforeAdjustment = versionOf(member);

        update(member.getId(), "Nimi", member.getEmail(), "MEMBER", true, false, versionBeforeAdjustment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(-1500));

        assertThat(balanceOf(member)).isEqualTo(-1500).isEqualTo(ledgerSumOf(member));
    }

    @Test
    void update_staleVersion_returns409AndChangesNothing() throws Exception {
        long loadedVersion = versionOf(member);
        update(member.getId(), "Esimene", member.getEmail(), "MEMBER", true, false, loadedVersion);

        update(member.getId(), "Teine", member.getEmail(), "MEMBER", true, false, loadedVersion)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(105));

        assertThat(entityManager.find(Member.class, member.getId()).getDisplayName()).isEqualTo("Esimene");
    }

    @Test
    void update_emailOfAnotherMember_returns403() throws Exception {
        update(member.getId(), "Nimi", admin.getEmail().toUpperCase(), "MEMBER", true, false, versionOf(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(133));
    }

    @Test
    void update_ownEmailInOtherCase_isAllowed() throws Exception {
        update(member.getId(), "Nimi", member.getEmail().toUpperCase(), "MEMBER", true, false, versionOf(member))
                .andExpect(status().isOk());
    }

    @Test
    void update_newEmail_closesLinksSentToTheOldOne() throws Exception {
        send(post("/api/admin/members/" + member.getId() + "/invite"), adminSession).andExpect(status().isNoContent());

        update(member.getId(), "Nimi", newEmail, "MEMBER", true, false, versionOf(member)).andExpect(status().isOk());

        assertThat(openLinksOf(member.getId())).isEmpty();
    }

    @Test
    void update_deactivate_endsTheMembersSession() throws Exception {
        update(member.getId(), "Nimi", member.getEmail(), "MEMBER", false, false, versionOf(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        send(get("/api/me"), memberSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(112));
    }

    @Test
    void update_removeAdminRole_endsThatAdminSession() throws Exception {
        Member otherAdmin = testSessions.createMember(MemberRoleEnum.ADMIN);
        MockHttpSession otherAdminSession = testSessions.passwordSession(otherAdmin);
        forget();

        update(otherAdmin.getId(), "Endine Admin", otherAdmin.getEmail(), "MEMBER", true, false, versionOf(otherAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        send(get("/api/admin/members"), otherAdminSession)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(118));
        assertThat(otherAdminSession.isInvalid()).isTrue();
    }

    @Test
    void update_adminDemotesThemselvesWhileAnotherAdminExists_isAllowed() throws Exception {
        update(admin.getId(), "Haldaja", admin.getEmail(), "MEMBER", true, false, versionOf(admin))
                .andExpect(status().isOk());

        send(get("/api/admin/members"), adminSession).andExpect(status().isForbidden());
    }

    @Test
    void update_lastActiveAdminLosesRights_returns403AndChangesNothing() throws Exception {
        deactivateAllAdminsExcept(admin);

        update(admin.getId(), "Haldaja", admin.getEmail(), "MEMBER", true, false, versionOf(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(134));
        update(admin.getId(), "Haldaja", admin.getEmail(), "ADMIN", false, false, versionOf(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(134));

        Member saved = entityManager.find(Member.class, admin.getId());
        assertThat(saved.getRole()).isEqualTo(MemberRoleEnum.ADMIN);
        assertThat(saved.getActive()).isTrue();
    }

    @Test
    void update_lastActiveAdminChangesOnlyName_isAllowed() throws Exception {
        deactivateAllAdminsExcept(admin);

        update(admin.getId(), "Ainus Admin", admin.getEmail(), "ADMIN", true, true, versionOf(admin))
                .andExpect(status().isOk());
    }

    @Test
    void update_unknownMember_returns404() throws Exception {
        update(Long.MAX_VALUE, "Nimi", newEmail, "MEMBER", true, false, 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(101));
    }

    @Test
    void update_withoutVersion_returns400() throws Exception {
        sendJson(put("/api/admin/members/" + member.getId()), adminSession,
                "{\"displayName\":\"Nimi\",\"email\":\"" + newEmail + "\",\"role\":\"MEMBER\",\"active\":true,\"feeExempt\":false,"
                        + "\"levelId\":" + member.getLevel().getId() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());
    }

    // ---- PIN ----

    @Test
    void resetPin_oldPinStopsWorkingAndNewOneIsReturnedOnce() throws Exception {
        String oldPinHmac = member.getPinHmac();

        String json = send(post("/api/admin/members/" + member.getId() + "/pin"), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pin").value(matchesPattern("\\d{6}")))
                .andReturn().getResponse().getContentAsString();

        Member saved = entityManager.find(Member.class, member.getId());
        assertThat(saved.getPinHmac()).isNotEqualTo(oldPinHmac).isEqualTo(pinService.hash(JsonPath.read(json, "$.pin")));
        assertThat(saved.getPinChangedAt()).isNotNull();
        assertThat(pinService.findByPin(JsonPath.read(json, "$.pin"))).get()
                .extracting(Member::getId).isEqualTo(member.getId());
    }

    @Test
    void resetPin_doesNotLogThePin(CapturedOutput output) throws Exception {
        String json = send(post("/api/admin/members/" + member.getId() + "/pin"), adminSession)
                .andReturn().getResponse().getContentAsString();

        assertThat(output.getOut()).contains("PIN reset by admin").doesNotContain((String) JsonPath.read(json, "$.pin"));
    }

    @Test
    void resetPin_byMember_returns403AndKeepsPin() throws Exception {
        send(post("/api/admin/members/" + member.getId() + "/pin"), memberSession)
                .andExpect(status().isForbidden());

        assertThat(entityManager.find(Member.class, member.getId()).getPinHmac()).isEqualTo(member.getPinHmac());
    }

    @Test
    void resetPin_unknownMember_returns404() throws Exception {
        send(post("/api/admin/members/" + Long.MAX_VALUE + "/pin"), adminSession)
                .andExpect(status().isNotFound());
    }

    // ---- adjustments ----

    @Test
    void adjustment_writesOneLedgerRowWithNoteAndChangesBalance() throws Exception {
        adjust(member.getId(), "{\"amountCents\":-1250,\"note\":\" katkine klaas \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(Math.toIntExact(member.getId())))
                .andExpect(jsonPath("$.balanceCents").value(-1250));

        assertThat(ledgerRowsOf(member)).singleElement().satisfies(entry -> {
            assertThat(entry.getType()).isEqualTo(AccountEntryTypeEnum.ADJUSTMENT);
            assertThat(entry.getAmountCents()).isEqualTo(-1250);
            assertThat(entry.getNote()).isEqualTo("katkine klaas");
            assertThat(entry.getCreatedBy().getId()).isEqualTo(admin.getId());
        });
        assertThat(balanceOf(member)).isEqualTo(-1250).isEqualTo(ledgerSumOf(member));
    }

    @Test
    void adjustment_positive_raisesBalanceAndShowsInMembersHistory() throws Exception {
        adjust(member.getId(), "{\"amountCents\":500,\"note\":\"tagastus\"}").andExpect(status().isCreated());

        assertThat(balanceOf(member)).isEqualTo(500).isEqualTo(ledgerSumOf(member));
        send(get("/api/me/entries"), memberSession)
                .andExpect(jsonPath("$.entries[0].description").value("Parandus · tagastus"))
                .andExpect(jsonPath("$.entries[0].amountCents").value(500));
    }

    @Test
    void adjustment_inactiveMember_isAllowed() throws Exception {
        update(member.getId(), "Nimi", member.getEmail(), "MEMBER", false, false, versionOf(member));

        adjust(member.getId(), "{\"amountCents\":-100,\"note\":\"viimane arve\"}").andExpect(status().isCreated());

        assertThat(balanceOf(member)).isEqualTo(-100);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"amountCents\":0,\"note\":\"null\"}",
            "{\"note\":\"summa puudu\"}",
            "{\"amountCents\":10000001,\"note\":\"liiga suur\"}",
            "{\"amountCents\":-10000001,\"note\":\"liiga suur\"}",
            "{\"amountCents\":100}",
            "{\"amountCents\":100,\"note\":\"   \"}"
    })
    void adjustment_invalidFields_returns400AndWritesNothing(String body) throws Exception {
        adjust(member.getId(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));

        assertThat(ledgerRowsOf(member)).isEmpty();
    }

    @Test
    void adjustment_zero_namesTheAmountField() throws Exception {
        adjust(member.getId(), "{\"amountCents\":0,\"note\":\"null\"}")
                .andExpect(jsonPath("$.fieldErrors.amountCentsNotZero").value("must not be 0"));
    }

    @Test
    void adjustment_noteTooLong_returns400() throws Exception {
        adjust(member.getId(), "{\"amountCents\":100,\"note\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustment_fractionalAmount_returns400() throws Exception {
        adjust(member.getId(), "{\"amountCents\":12.5,\"note\":\"murd\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustment_byMemberToOwnBalance_returns403AndWritesNothing() throws Exception {
        sendJson(post("/api/admin/members/" + member.getId() + "/adjustments"), memberSession,
                "{\"amountCents\":100000,\"note\":\"kingitus\"}")
                .andExpect(status().isForbidden());

        assertThat(ledgerRowsOf(member)).isEmpty();
    }

    @Test
    void adjustment_unknownMember_returns404() throws Exception {
        adjust(Long.MAX_VALUE, "{\"amountCents\":100,\"note\":\"keegi\"}")
                .andExpect(status().isNotFound());
    }

    // ---- purchases ----

    @Test
    void purchases_newestFirstWithLinesAndUndoneOnesMarked() throws Exception {
        Product lager = testData.product("B17 Lager " + UUID.randomUUID(), 350, 1, true);
        Product stout = testData.product("B17 Stout " + UUID.randomUUID(), 450, 2, true);
        Long first = buy(1150, line(lager, 2), line(stout, 1));
        Long second = buy(350, line(lager, 1));
        send(post("/api/purchases/" + first + "/undo"), adminSession).andExpect(status().isOk());

        send(get("/api/admin/members/" + member.getId() + "/purchases"), adminSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchases.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.purchases[0].purchaseId").value(Math.toIntExact(second)))
                .andExpect(jsonPath("$.purchases[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.purchases[0].reversedAt").value(nullValue()))
                .andExpect(jsonPath("$.purchases[1].purchaseId").value(Math.toIntExact(first)))
                .andExpect(jsonPath("$.purchases[1].status").value("REVERSED"))
                .andExpect(jsonPath("$.purchases[1].totalCents").value(1150))
                .andExpect(jsonPath("$.purchases[1].reversedAt").isNotEmpty())
                .andExpect(jsonPath("$.purchases[1].reversedByName").value("Haldaja Admin"))
                .andExpect(jsonPath("$.purchases[1].lines.length()").value(2))
                .andExpect(jsonPath("$.purchases[1].lines[0].name").value(lager.getName()))
                .andExpect(jsonPath("$.purchases[1].lines[0].quantity").value(2))
                .andExpect(jsonPath("$.purchases[1].lines[1].name").value(stout.getName()));
        assertThat(balanceOf(member)).isEqualTo(-350).isEqualTo(ledgerSumOf(member));
    }

    @Test
    void purchases_pagesOf20() throws Exception {
        for (int i = 0; i < PurchaseService.MEMBER_PURCHASES_PAGE_SIZE + 1; i++) {
            savePurchase(member, OffsetDateTime.now().minusMinutes(i));
        }
        forget();

        send(get("/api/admin/members/" + member.getId() + "/purchases"), adminSession)
                .andExpect(jsonPath("$.purchases.length()").value(PurchaseService.MEMBER_PURCHASES_PAGE_SIZE))
                .andExpect(jsonPath("$.hasMore").value(true));
        send(get("/api/admin/members/" + member.getId() + "/purchases").param("page", "1"), adminSession)
                .andExpect(jsonPath("$.purchases.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void purchases_onlyThisMembers() throws Exception {
        savePurchase(admin, OffsetDateTime.now());
        forget();

        send(get("/api/admin/members/" + member.getId() + "/purchases"), adminSession)
                .andExpect(jsonPath("$.purchases.length()").value(0));
    }

    @Test
    void purchases_negativePage_returns400() throws Exception {
        send(get("/api/admin/members/" + member.getId() + "/purchases").param("page", "-1"), adminSession)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(103));
    }

    @Test
    void purchases_unknownMember_returns404() throws Exception {
        send(get("/api/admin/members/" + Long.MAX_VALUE + "/purchases"), adminSession)
                .andExpect(status().isNotFound());
    }

    @Test
    void purchases_byMember_returns403() throws Exception {
        send(get("/api/admin/members/" + admin.getId() + "/purchases"), memberSession)
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private ResultActions create(String displayName, String email, String role, boolean feeExempt) throws Exception {
        return sendJson(post("/api/admin/members"), adminSession, createBody(displayName, email, role, feeExempt));
    }

    private static String createBody(String displayName, String email, String role, boolean feeExempt) {
        return "{\"displayName\":\"" + displayName + "\",\"email\":\"" + email + "\",\"role\":\"" + role
                + "\",\"feeExempt\":" + feeExempt + "}";
    }

    // Keeps the member's current level
    private ResultActions update(Long memberId, String displayName, String email, String role, boolean active,
                                 boolean feeExempt, long version) throws Exception {
        Member current = entityManager.find(Member.class, memberId);
        Long levelId = current != null ? current.getLevel().getId() : firstActiveLevel().getId();
        return update(memberId, displayName, email, role, active, feeExempt, version, levelId);
    }

    private ResultActions update(Long memberId, String displayName, String email, String role, boolean active,
                                 boolean feeExempt, long version, Long levelId) throws Exception {
        return sendJson(put("/api/admin/members/" + memberId), adminSession,
                "{\"displayName\":\"" + displayName + "\",\"email\":\"" + email + "\",\"role\":\"" + role
                        + "\",\"active\":" + active + ",\"feeExempt\":" + feeExempt + ",\"version\":" + version
                        + ",\"levelId\":" + levelId + "}");
    }

    private MembershipLevel firstActiveLevel() {
        return membershipLevelRepository.findAllActive().getFirst();
    }

    private MembershipLevel level(String name, boolean active) {
        MembershipLevel level = new MembershipLevel();
        level.setName(name + " " + UUID.randomUUID());
        level.setSortOrder(100);
        level.setActive(active);
        level.setCreatedAt(OffsetDateTime.now());
        entityManager.persist(level);
        forget();
        return level;
    }

    private ResultActions adjust(Long memberId, String body) throws Exception {
        return sendJson(post("/api/admin/members/" + memberId + "/adjustments"), adminSession, body);
    }

    private Long buy(long expectedTotalCents, String... lines) throws Exception {
        String json = sendJson(post("/api/purchases"), memberSession,
                "{\"requestId\":\"" + UUID.randomUUID() + "\",\"expectedTotalCents\":" + expectedTotalCents
                        + ",\"lines\":[" + String.join(",", lines) + "]}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(json, "$.purchaseId");
    }

    private static String line(Product product, int quantity) {
        return "{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}";
    }

    // Without lines or ledger rows: only for the list's paging and filtering
    private void savePurchase(Member owner, OffsetDateTime createdAt) {
        Purchase purchase = new Purchase();
        purchase.setMember(entityManager.find(Member.class, owner.getId()));
        purchase.setTotalCents(100L);
        purchase.setStatus(PurchaseStatusEnum.COMPLETED);
        purchase.setCreatedAt(createdAt);
        entityManager.persist(purchase);
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

    // Inside the test transaction only: rolled back afterwards like everything else
    private void deactivateAllAdminsExcept(Member keep) {
        entityManager.createQuery("update Member m set m.active = false where m.role = :role and m.id <> :id")
                .setParameter("role", MemberRoleEnum.ADMIN)
                .setParameter("id", keep.getId())
                .executeUpdate();
        forget();
    }

    private static String memberPath(Member someone) {
        return "$[?(@.memberId == " + someone.getId() + ")]";
    }

    private long versionOf(Member someone) {
        forget();
        return entityManager.find(Member.class, someone.getId()).getVersion();
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

    private List<AccountEntry> ledgerRowsOf(Member someone) {
        return entityManager.createQuery(
                        "select e from AccountEntry e join fetch e.createdBy where e.member.id = :id order by e.id", AccountEntry.class)
                .setParameter("id", someone.getId()).getResultList();
    }

    private List<PasswordToken> openLinksOf(Long memberId) {
        return entityManager.createQuery(
                        "select t from PasswordToken t join fetch t.createdBy where t.member.id = :id and t.usedAt is null",
                        PasswordToken.class)
                .setParameter("id", memberId).getResultList();
    }

    private long memberCountWithEmail(String email) {
        return entityManager.createQuery("select count(m) from Member m where lower(m.email) = lower(:email)", Long.class)
                .setParameter("email", email).getSingleResult();
    }

    // All requests share the test transaction: write and forget, as a commit would between real requests
    private void forget() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Long idOf(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }
}
