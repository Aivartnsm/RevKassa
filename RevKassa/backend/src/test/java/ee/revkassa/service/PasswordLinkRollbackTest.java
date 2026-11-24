package ee.revkassa.service;

import ee.revkassa.infrastructure.mail.MailSendFailedException;
import ee.revkassa.infrastructure.security.PinService;
import ee.revkassa.persistence.member.Member;
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
 * previous link must stay open. The test removes its own rows afterwards.
 */
@SpringBootTest
@Import({TestData.class, FakeMailer.Config.class})
class PasswordLinkRollbackTest {

    @Autowired
    private PasswordLinkService passwordLinkService;
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
            entityManager.createQuery("delete from PasswordToken t where t.member.id = :id").setParameter("id", memberId).executeUpdate();
            entityManager.createQuery("delete from Member m where m.id = :id").setParameter("id", memberId).executeUpdate();
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

    private Member member() {
        return transactionTemplate.execute(status -> entityManager.find(Member.class, memberId));
    }

    private List<PasswordToken> tokensOfMember() {
        return transactionTemplate.execute(status -> entityManager
                .createQuery("select t from PasswordToken t where t.member.id = :id", PasswordToken.class)
                .setParameter("id", memberId).getResultList());
    }
}
