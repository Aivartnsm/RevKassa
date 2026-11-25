package ee.revkassa.service;

import ee.revkassa.controller.admin.member.dto.MemberPurchaseDto;
import ee.revkassa.controller.admin.member.dto.MemberPurchasePageDto;
import ee.revkassa.controller.purchase.dto.PurchaseCreateRequestDto;
import ee.revkassa.controller.purchase.dto.PurchaseDto;
import ee.revkassa.controller.purchase.dto.PurchaseLineRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ConflictException;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.security.LoginMethodEnum;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.product.ProductRepository;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.purchase.PurchaseMapper;
import ee.revkassa.persistence.purchase.PurchaseRepository;
import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import ee.revkassa.persistence.purchaseline.PurchaseLine;
import ee.revkassa.persistence.purchaseline.PurchaseLineMapper;
import ee.revkassa.persistence.purchaseline.PurchaseLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A member buys for their own balance (guests too, on the member's balance). One transaction writes the purchase,
 * its lines with the prices of this moment, one SALE stock movement per line and one PURCHASE ledger row.
 * No balance limit, and stock may go negative: the fridge is the truth.
 * Undo never deletes anything: the purchase is marked REVERSED and PURCHASE_REVERSAL and SALE_REVERSAL rows are added.
 * Lock order (same in undo): the member's row first, then products in id order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    public static final Duration UNDO_WINDOW = Duration.ofMinutes(10);
    public static final int MEMBER_PURCHASES_PAGE_SIZE = 20;

    private final PurchaseRepository purchaseRepository;
    private final PurchaseMapper purchaseMapper;
    private final PurchaseLineRepository purchaseLineRepository;
    private final PurchaseLineMapper purchaseLineMapper;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final StockService stockService;
    private final AccountService accountService;
    private final Clock clock;

    /**
     * @param created false when this requestId was already recorded: then the earlier purchase is returned
     */
    public record PurchaseResult(PurchaseDto purchase, boolean created) {
    }

    @Transactional
    public PurchaseResult createPurchase(Long memberId, PurchaseCreateRequestDto request) {
        Member member = getValidMemberForUpdateBy(memberId);
        Optional<Purchase> earlier = purchaseRepository.findByRequestId(request.getRequestId());
        if (earlier.isPresent()) {
            return new PurchaseResult(toRecordedPurchaseDto(validateOwnPurchase(earlier.get(), member)), false);
        }
        SortedMap<Long, Integer> quantities = mergeLinesInProductIdOrder(request.getLines());
        Map<Long, Product> products = getAvailableProductsBy(quantities);
        long totalCents = calculateTotal(quantities, products);
        validateExpectedTotal(totalCents, request.getExpectedTotalCents());
        Purchase purchase = purchaseRepository.save(createPurchase(member, totalCents, request.getRequestId()));
        List<PurchaseLine> lines = saveLinesAndMoveStock(purchase, quantities, products, member);
        long balanceCents = accountService.post(AccountPosting.purchase(member, totalCents, purchase));
        log.info("Purchase recorded: purchaseId={}, memberId={}, lines={}", purchase.getId(), memberId, lines.size());
        return new PurchaseResult(toPurchaseDto(purchase, lines, balanceCents), true);
    }

    /**
     * The member may undo their own purchase within UNDO_WINDOW; an admin with a password session may undo any purchase
     * at any time. Another member's purchase answers as if it did not exist.
     */
    @Transactional
    public PurchaseDto undoPurchase(Long purchaseId, Long actorId, LoginMethodEnum loginMethod) {
        Member owner = getValidMemberForUpdateBy(getValidOwnerIdBy(purchaseId));
        Member actor = actorId.equals(owner.getId()) ? owner : getValidMemberBy(actorId);
        Purchase purchase = getValidPurchaseBy(purchaseId);
        boolean admin = isAdmin(actor, loginMethod);
        validateMayUndo(purchase, actor, admin);
        List<PurchaseLine> lines = purchaseLineRepository.findAllByPurchaseId(purchaseId);
        moveStockBackInProductIdOrder(purchase, lines, actor);
        markReversed(purchase, actor);
        long balanceCents = accountService.post(AccountPosting.purchaseReversal(owner, purchase, actor));
        log.info("Purchase undone: purchaseId={}, memberId={}, reversedBy={}", purchaseId, owner.getId(), actor.getId());
        return toPurchaseDto(purchase, lines, balanceCents);
    }

    /**
     * For the admin: one member's purchases newest first, undone ones included, with their lines.
     */
    @Transactional(readOnly = true)
    public MemberPurchasePageDto getMemberPurchases(Long memberId, int page) {
        validateMemberExists(memberId);
        Slice<Purchase> purchases = purchaseRepository.findMemberPurchases(memberId,
                PageRequest.of(page, MEMBER_PURCHASES_PAGE_SIZE));
        Map<Long, List<PurchaseLine>> linesByPurchaseId = getLinesOf(purchases.getContent());
        List<MemberPurchaseDto> dtos = purchases.getContent().stream()
                .map(purchase -> toMemberPurchaseDto(purchase, linesByPurchaseId.getOrDefault(purchase.getId(), List.of())))
                .toList();
        return new MemberPurchasePageDto(dtos, page, purchases.hasNext());
    }

    private Long getValidOwnerIdBy(Long purchaseId) {
        return purchaseRepository.findMemberId(purchaseId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private Member getValidMemberForUpdateBy(Long memberId) {
        return memberRepository.findForUpdate(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Read after the owner's row is locked: a parallel undo of the same purchase has finished by now
    private Purchase getValidPurchaseBy(Long purchaseId) {
        return purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    // Read fresh from the database, like the session's ROLE_ADMIN: a PIN never gives admin rights
    private static boolean isAdmin(Member actor, LoginMethodEnum loginMethod) {
        return loginMethod == LoginMethodEnum.PASSWORD && actor.getRole() == MemberRoleEnum.ADMIN;
    }

    private void validateMayUndo(Purchase purchase, Member actor, boolean admin) {
        if (!admin && !purchase.getMember().getId().equals(actor.getId())) {
            log.warn("Undo of another member's purchase refused: purchaseId={}, memberId={}", purchase.getId(), actor.getId());
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
        if (purchase.getStatus() == PurchaseStatusEnum.REVERSED) {
            throw new ForbiddenException(Error.PURCHASE_ALREADY_REVERSED);
        }
        if (!admin && OffsetDateTime.now(clock).isAfter(purchase.getCreatedAt().plus(UNDO_WINDOW))) {
            throw new ForbiddenException(Error.UNDO_WINDOW_PASSED);
        }
    }

    // Product id order, the same lock order as when buying
    private void moveStockBackInProductIdOrder(Purchase purchase, List<PurchaseLine> lines, Member actor) {
        lines.stream()
                .sorted(Comparator.comparing(line -> line.getProduct().getId()))
                .forEach(line -> stockService.move(StockMove.saleReversal(line.getProduct(), line.getQuantity(), purchase, actor)));
    }

    private void markReversed(Purchase purchase, Member actor) {
        purchase.setStatus(PurchaseStatusEnum.REVERSED);
        purchase.setReversedAt(OffsetDateTime.now(clock));
        purchase.setReversedBy(actor);
    }

    // A requestId is a random UUID; another member's id can only be a client bug or a guess, never a reason to show it
    private static Purchase validateOwnPurchase(Purchase purchase, Member member) {
        if (!purchase.getMember().getId().equals(member.getId())) {
            log.warn("Purchase requestId of another member used: memberId={}", member.getId());
            throw new ForbiddenException(Error.INVALID_REQUEST);
        }
        return purchase;
    }

    private PurchaseDto toRecordedPurchaseDto(Purchase purchase) {
        List<PurchaseLine> lines = purchaseLineRepository.findAllByPurchaseId(purchase.getId());
        return toPurchaseDto(purchase, lines, memberRepository.findBalanceCents(purchase.getMember().getId()));
    }

    // The same product twice becomes one line; id order is the lock order for the stock rows
    private static SortedMap<Long, Integer> mergeLinesInProductIdOrder(List<PurchaseLineRequestDto> lines) {
        SortedMap<Long, Integer> quantities = new TreeMap<>();
        lines.forEach(line -> quantities.merge(line.getProductId(), line.getQuantity(), Integer::sum));
        return quantities;
    }

    private Map<Long, Product> getAvailableProductsBy(SortedMap<Long, Integer> quantities) {
        Map<Long, Product> products = productRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != quantities.size()) {
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
        if (products.values().stream().anyMatch(product -> !product.getActive())) {
            throw new ForbiddenException(Error.PRODUCT_NOT_AVAILABLE);
        }
        return products;
    }

    private static long calculateTotal(SortedMap<Long, Integer> quantities, Map<Long, Product> products) {
        long totalCents = 0;
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            totalCents = Math.addExact(totalCents, Math.multiplyExact(products.get(line.getKey()).getPriceCents(), line.getValue()));
        }
        return totalCents;
    }

    // The member confirmed the total they saw; a price changed since then must not be charged silently
    private static void validateExpectedTotal(long totalCents, long expectedTotalCents) {
        if (totalCents != expectedTotalCents) {
            throw new ConflictException(Error.PRICES_CHANGED);
        }
    }

    private Purchase createPurchase(Member member, long totalCents, UUID requestId) {
        Purchase purchase = new Purchase();
        purchase.setMember(member);
        purchase.setTotalCents(totalCents);
        purchase.setStatus(PurchaseStatusEnum.COMPLETED);
        purchase.setCreatedAt(OffsetDateTime.now(clock));
        purchase.setRequestId(requestId);
        return purchase;
    }

    private List<PurchaseLine> saveLinesAndMoveStock(Purchase purchase, SortedMap<Long, Integer> quantities,
                                                     Map<Long, Product> products, Member member) {
        List<PurchaseLine> lines = new ArrayList<>();
        quantities.forEach((productId, quantity) -> {
            Product product = products.get(productId);
            lines.add(purchaseLineRepository.save(createLine(purchase, product, quantity)));
            stockService.move(StockMove.sale(product, quantity, purchase, member));
        });
        return lines;
    }

    private static PurchaseLine createLine(Purchase purchase, Product product, int quantity) {
        PurchaseLine line = new PurchaseLine();
        line.setPurchase(purchase);
        line.setProduct(product);
        line.setQuantity(quantity);
        line.setUnitPriceCents(product.getPriceCents());
        return line;
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
    }

    // All lines of the page in one query
    private Map<Long, List<PurchaseLine>> getLinesOf(List<Purchase> purchases) {
        if (purchases.isEmpty()) {
            return Map.of();
        }
        List<Long> purchaseIds = purchases.stream().map(Purchase::getId).toList();
        return purchaseLineRepository.findAllByPurchaseIds(purchaseIds).stream()
                .collect(Collectors.groupingBy(line -> line.getPurchase().getId()));
    }

    private MemberPurchaseDto toMemberPurchaseDto(Purchase purchase, List<PurchaseLine> lines) {
        MemberPurchaseDto dto = purchaseMapper.toMemberPurchaseDto(purchase);
        dto.setLines(purchaseLineMapper.toPurchaseLineDtos(lines));
        return dto;
    }

    private PurchaseDto toPurchaseDto(Purchase purchase, List<PurchaseLine> lines, long balanceCents) {
        PurchaseDto dto = new PurchaseDto();
        dto.setPurchaseId(purchase.getId());
        dto.setStatus(purchase.getStatus());
        dto.setTotalCents(purchase.getTotalCents());
        dto.setBalanceCents(balanceCents);
        dto.setCreatedAt(purchase.getCreatedAt());
        dto.setUndoableUntil(purchase.getCreatedAt().plus(UNDO_WINDOW));
        dto.setLines(purchaseLineMapper.toPurchaseLineDtos(lines));
        return dto;
    }
}
