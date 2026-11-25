package ee.revkassa.service;

import ee.revkassa.controller.admin.member.dto.MemberCreateRequestDto;
import ee.revkassa.infrastructure.mail.MailSendFailedException;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.passwordtoken.PasswordToken;
import ee.revkassa.persistence.passwordtoken.PasswordTokenPurposeEnum;
import ee.revkassa.testdata.FakeMailer;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real commits (no test transaction): when the email fails, the new link must not be saved and the member's
 * previous link must stay open; a new member whose invite fails is not saved at all (B17).
 * The test removes its own rows afterwards.
 */
@SpringBootTest
@Import({TestData.class, FakeMailer.Config.class})
class PasswordLinkRollbackTest {

    private static final String NEW_MEMBER_EMAIL = "rollback-new-member@example.org";

    @Autowired
    private PasswordLinkService passwordLinkService;
    @Autowired
    private MemberService memberService;
    @Autowired
    private FakeMailer fakeMailer;
    @Autowired
    private TestData testData;
    @Autowired
    private PinService pinService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long memberId;

    @BeforeEach
    void createMember() {
        fakeMailer.clear();
        memberId = transactionTemplate.execute(status ->
                testData.member("rollback-member@example.org", pinService.hash("749999")).getId());
    }

    @AfterEach
    void removeRows() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("delete from PasswordToken t where t.member.id = :id or t.member.email = :email")
                    .setParameter("id", memberId).setParameter("email", NEW_MEMBER_EMAIL).executeUpdate();
            entityManager.createQuery("delete from Member m where m.id = :id or m.email = :email")
                    .setParameter("id", memberId).setParameter("email", NEW_MEMBER_EMAIL).executeUpdate();
        });
    }

    @Test
    void mailFailure_savesNothingAndKeepsPreviousLinkOpen() {
        passwordLinkService.send(member(), PasswordTokenPurposeEnum.RESET, null);
        fakeMailer.failNextSend();

        assertThatThrownBy(() -> passwordLinkService.send(member(), PasswordTokenPurposeEnum.RESET, null))
                .isInstanceOf(MailSendFailedException.class);

        List<PasswordToken> tokens = tokensOfMember();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().getUsedAt()).isNull();
    }

    @Test
    void createMember_mailFailure_savesNoMember() {
        MemberCreateRequestDto request = new MemberCreateRequestDto();
        request.setDisplayName("Kirjata Liige");
        request.setEmail(NEW_MEMBER_EMAIL);
        request.setRole(MemberRoleEnum.MEMBER);
        request.setFeeExempt(false);
        fakeMailer.failNextSend();

        assertThatThrownBy(() -> memberService.createMember(request, memberId))
                .isInstanceOf(MailSendFailedException.class);

        long saved = transactionTemplate.execute(status -> entityManager
                .createQuery("select count(m) from Member m where m.email = :email", Long.class)
                .setParameter("email", NEW_MEMBER_EMAIL).getSingleResult());
        assertThat(saved).isZero();
    }

    private Member member() {
        return transactionTemplate.execute(status -> entityManager.find(Member.class, memberId));
    }

    private List<PasswordToken> tokensOfMember() {
        return transactionTemplate.execute(status -> entityManager
                .createQuery("select t from PasswordToken t where t.member.id = :id", PasswordToken.class)
                .setParameter("id", memberId).getResultList());
    }
}
