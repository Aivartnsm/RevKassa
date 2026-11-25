package ee.revkassa.service;

import ee.revkassa.controller.purchase.dto.PurchaseCreateRequestDto;
import ee.revkassa.controller.purchase.dto.PurchaseDto;
import ee.revkassa.controller.purchase.dto.PurchaseLineRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ConflictException;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.product.ProductRepository;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.purchase.PurchaseRepository;
import ee.revkassa.persistence.purchase.PurchaseStatusEnum;
import ee.revkassa.persistence.purchaseline.PurchaseLine;
import ee.revkassa.persistence.purchaseline.PurchaseLineMapper;
import ee.revkassa.persistence.purchaseline.PurchaseLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * Lock order (same in undo): the member's row first, then products in id order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    public static final Duration UNDO_WINDOW = Duration.ofMinutes(10);

    private final PurchaseRepository purchaseRepository;
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

    private Member getValidMemberForUpdateBy(Long memberId) {
        return memberRepository.findForUpdate(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
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
