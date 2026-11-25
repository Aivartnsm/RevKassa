package ee.revkassa.service;

import ee.revkassa.controller.me.dto.AccountEntryDto;
import ee.revkassa.controller.me.dto.AccountEntryPageDto;
import ee.revkassa.persistence.accountentry.AccountEntry;
import ee.revkassa.persistence.accountentry.AccountEntryMapper;
import ee.revkassa.persistence.accountentry.AccountEntryRepository;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.purchaseline.PurchaseLine;
import ee.revkassa.persistence.purchaseline.PurchaseLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A member's balance history: the ledger rows newest first, each with a description in Estonian, as the member reads it
 * in the app (and later in invoice emails).
 */
@Service
@RequiredArgsConstructor
public class AccountEntryService {

    public static final int PAGE_SIZE = 20;

    private static final String LINE_SEPARATOR = ", ";
    private static final String NOTE_SEPARATOR = " · ";
    private static final String REVERSED_PURCHASE = "Tagasivõetud ost: ";
    private static final String PAYMENT = "Makse";
    private static final String ADJUSTMENT = "Parandus";
    private static final String MEMBERSHIP_FEE = "Liikmemaks";
    private static final String MEMBERSHIP_FEE_WAIVER = "Liikmemaksu tühistus";
    private static final String EVENT_FEE = "Ürituse tasu";
    private static final String EVENT_FEE_REVERSAL = "Ürituse tasu tagastus";

    private final AccountEntryRepository accountEntryRepository;
    private final AccountEntryMapper accountEntryMapper;
    private final PurchaseLineRepository purchaseLineRepository;

    @Transactional(readOnly = true)
    public AccountEntryPageDto getMemberEntries(Long memberId, int page) {
        Slice<AccountEntry> entries = accountEntryRepository.findMemberEntries(memberId, PageRequest.of(page, PAGE_SIZE));
        Map<Long, String> purchaseDescriptions = describePurchasesOf(entries.getContent());
        List<AccountEntryDto> dtos = entries.getContent().stream()
                .map(entry -> toAccountEntryDto(entry, purchaseDescriptions))
                .toList();
        return new AccountEntryPageDto(dtos, page, entries.hasNext());
    }

    // All lines of the page's purchases in one query: "2 × Saku Originaal, 1 × Õllenaut IPA" per purchase id
    private Map<Long, String> describePurchasesOf(List<AccountEntry> entries) {
        List<Long> purchaseIds = entries.stream()
                .map(AccountEntry::getPurchase)
                .filter(Objects::nonNull)
                .map(Purchase::getId)
                .distinct()
                .toList();
        if (purchaseIds.isEmpty()) {
            return Map.of();
        }
        return purchaseLineRepository.findAllByPurchaseIds(purchaseIds).stream()
                .collect(Collectors.groupingBy(line -> line.getPurchase().getId(),
                        Collectors.mapping(AccountEntryService::describeLine, Collectors.joining(LINE_SEPARATOR))));
    }

    private static String describeLine(PurchaseLine line) {
        return line.getQuantity() + " × " + line.getProduct().getName();
    }

    private AccountEntryDto toAccountEntryDto(AccountEntry entry, Map<Long, String> purchaseDescriptions) {
        AccountEntryDto dto = accountEntryMapper.toAccountEntryDto(entry);
        dto.setDescription(describe(entry, purchaseDescriptions));
        return dto;
    }

    private static String describe(AccountEntry entry, Map<Long, String> purchaseDescriptions) {
        return switch (entry.getType()) {
            case PURCHASE -> purchaseDescriptions.get(entry.getPurchase().getId());
            case PURCHASE_REVERSAL -> REVERSED_PURCHASE + purchaseDescriptions.get(entry.getPurchase().getId());
            case PAYMENT -> PAYMENT + NOTE_SEPARATOR + entry.getPayment().getMethod().getDescription();
            case ADJUSTMENT -> withNote(ADJUSTMENT, entry.getNote());
            case MEMBERSHIP_FEE -> MEMBERSHIP_FEE;
            case MEMBERSHIP_FEE_WAIVER -> withNote(MEMBERSHIP_FEE_WAIVER, entry.getNote());
            case EVENT_FEE -> EVENT_FEE;
            case EVENT_FEE_REVERSAL -> EVENT_FEE_REVERSAL;
        };
    }

    private static String withNote(String text, String note) {
        return note == null ? text : text + NOTE_SEPARATOR + note;
    }
}
