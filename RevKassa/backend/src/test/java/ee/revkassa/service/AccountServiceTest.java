package ee.revkassa.service;

import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryRepository;
import ee.revkassa.persistence.accountentry.AccountEntryTypeEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.testdata.TestData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B10: AccountService.post against PostgreSQL. Parallel postings are in AccountServiceConcurrencyTest.
 */
@SpringBootTest
@Transactional
@Import(TestData.class)
class AccountServiceTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountEntryRepository accountEntryRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TestData testData;
    @Autowired
    private EntityManager entityManager;

    private Member member;
    private Member admin;

    @BeforeEach
    void createMembers() {
        member = testData.member("ledger-member@example.org", "ledger-pin-hmac-1");
        admin = testData.member("ledger-admin@example.org", "ledger-pin-hmac-2");
        admin.setRole(MemberRoleEnum.ADMIN);
    }

    @Test
    void post_writesEntryAndReturnsNewBalance() {
        long balance = accountService.post(AccountPosting.adjustment(member, -1250, " sularaha puudu ", admin));

        assertThat(balance).isEqualTo(-1250);
        AccountEntry entry = entriesOf(member).getFirst();
        assertThat(entry.getType()).isEqualTo(AccountEntryTypeEnum.ADJUSTMENT);
        assertThat(entry.getAmountCents()).isEqualTo(-1250);
        assertThat(entry.getNote()).isEqualTo("sularaha puudu");
        assertThat(entry.getCreatedBy().getId()).isEqualTo(admin.getId());
        assertThat(entry.getCreatedAt()).isNotNull();
        assertThat(entry.getPurchase()).isNull();
    }

    @Test
    void severalPostings_balanceEqualsLedgerSum() {
        accountService.post(AccountPosting.adjustment(member, -1250, "a", admin));
        accountService.post(AccountPosting.adjustment(member, 5000, "b", admin));
        long balance = accountService.post(AccountPosting.adjustment(member, -280, "c", admin));

        assertThat(balance).isEqualTo(3470).isEqualTo(accountEntryRepository.sumAmountCents(member.getId()));
        assertThat(freshBalanceOf(member)).isEqualTo(3470);
    }

    @Test
    void invariant_holdsForEveryMemberAfterPostings() {
        accountService.post(AccountPosting.adjustment(member, -1250, "a", admin));
        accountService.post(AccountPosting.adjustment(admin, 700, "b", admin));

        List<?> mismatches = entityManager.createNativeQuery("""
                select m.id from member m left join account_entry e on e.member_id = m.id
                group by m.id, m.balance_cents having m.balance_cents <> coalesce(sum(e.amount_cents), 0)""").getResultList();
        assertThat(mismatches).isEmpty();
    }

    @Test
    void post_doesNotChangeMemberVersion() {
        entityManager.flush();
        long versionBefore = member.getVersion();

        accountService.post(AccountPosting.adjustment(member, -1250, "a", admin));

        entityManager.clear();
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getVersion()).isEqualTo(versionBefore);
    }

    @Test
    void post_otherMembersAreNotTouched() {
        accountService.post(AccountPosting.adjustment(member, -1250, "a", admin));

        assertThat(freshBalanceOf(admin)).isZero();
    }

    @Test
    void savingAMemberLoadedBeforeAPosting_keepsThePostedBalance() {
        entityManager.flush();
        // the member form changes the name of the entity that still holds the old balance in memory
        accountService.post(AccountPosting.adjustment(member, -1250, "a", admin));
        member.setDisplayName("Uus Nimi");
        entityManager.flush();

        assertThat(freshBalanceOf(member)).isEqualTo(-1250);
    }

    @Test
    void invalidPosting_writesNothing() {
        long entriesBefore = accountEntryRepository.count();

        assertThatThrownBy(() -> accountService.post(AccountPosting.adjustment(member, 0, "a", admin)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(accountEntryRepository.count()).isEqualTo(entriesBefore);
        assertThat(freshBalanceOf(member)).isZero();
    }

    private long freshBalanceOf(Member someone) {
        entityManager.flush();
        entityManager.clear();
        return memberRepository.findById(someone.getId()).orElseThrow().getBalanceCents();
    }

    private List<AccountEntry> entriesOf(Member someone) {
        return entityManager.createQuery(
                        "select e from AccountEntry e join fetch e.createdBy where e.member.id = :id order by e.id",
                        AccountEntry.class)
                .setParameter("id", someone.getId()).getResultList();
    }
}
