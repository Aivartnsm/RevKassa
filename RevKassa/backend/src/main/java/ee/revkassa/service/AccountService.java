package ee.revkassa.service;

import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryRepository;
import ee.revkassa.persistence.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * The money ledger. post() is the only way a balance changes: it adds an account_entry row and changes
 * member.balance_cents by the same amount in the same transaction, so balance_cents = sum(amount_cents) always holds.
 * The balance is changed with one SQL statement, so parallel postings to the same member never lose an update and
 * never make the member's form stale (version is not touched). There is no balance limit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountEntryRepository accountEntryRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    /**
     * @return the member's balance after the posting; a Member already loaded in this transaction is not updated
     */
    @Transactional
    public long post(AccountPosting posting) {
        AccountEntry entry = accountEntryRepository.save(createEntry(posting));
        memberRepository.addToBalance(posting.member().getId(), posting.amountCents());
        long balanceCents = memberRepository.findBalanceCents(posting.member().getId());
        // Ids only: amounts and balances are the member's financial data and are in the ledger anyway
        log.info("Account entry posted: entryId={}, memberId={}, type={}", entry.getId(), posting.member().getId(), posting.type());
        return balanceCents;
    }

    private AccountEntry createEntry(AccountPosting posting) {
        AccountEntry entry = new AccountEntry();
        entry.setMember(posting.member());
        entry.setType(posting.type());
        entry.setAmountCents(posting.amountCents());
        entry.setPurchase(posting.purchase());
        entry.setPayment(posting.payment());
        entry.setMembershipChargeId(posting.membershipChargeId());
        entry.setEventParticipantId(posting.eventParticipantId());
        entry.setNote(posting.note());
        entry.setCreatedBy(posting.createdBy());
        entry.setCreatedAt(OffsetDateTime.now(clock));
        return entry;
    }
}
