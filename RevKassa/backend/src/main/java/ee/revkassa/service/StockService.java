package ee.revkassa.service;

import ee.revkassa.controller.admin.stock.dto.DeliveryCreateRequestDto;
import ee.revkassa.controller.admin.stock.dto.DeliveryLineDto;
import ee.revkassa.controller.admin.stock.dto.ProductStockDto;
import ee.revkassa.controller.admin.stock.dto.StocktakeCreateRequestDto;
import ee.revkassa.controller.admin.stock.dto.StocktakeLineDto;
import ee.revkassa.controller.admin.stock.dto.StocktakeResultDto;
import ee.revkassa.controller.admin.stock.dto.WriteOffCreateRequestDto;
import ee.revkassa.controller.admin.stock.dto.WriteOffLineDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.product.ProductRepository;
import ee.revkassa.persistence.stockmovement.StockMovement;
import ee.revkassa.persistence.stockmovement.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The stock ledger. move() is the only way stock changes: it adds a stock_movement row and changes
 * product.stock_qty by the same amount in the same transaction, so stock_qty = sum(quantity_delta) always holds.
 * Stock may go negative (the fridge is the truth; a sale is never blocked).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    /**
     * @return the product's stock after the move; a Product already loaded in this transaction is not updated
     */
    @Transactional
    public int move(StockMove stockMove) {
        stockMovementRepository.save(createMovement(stockMove));
        productRepository.addToStock(stockMove.product().getId(), stockMove.quantityDelta());
        return productRepository.findStockQty(stockMove.product().getId());
    }

    /**
     * All lines or none: an unknown product cancels the whole delivery.
     */
    @Transactional
    public List<ProductStockDto> recordDelivery(DeliveryCreateRequestDto request, Long adminId) {
        Map<Long, Product> products = getValidProductsBy(request.getLines().stream().map(DeliveryLineDto::getProductId).toList());
        Member admin = getValidMemberBy(adminId);
        String note = request.getNote();
        Map<Long, Integer> stockAfter = new LinkedHashMap<>();
        for (DeliveryLineDto line : request.getLines()) {
            Product product = products.get(line.getProductId());
            int stockQty = move(StockMove.delivery(product, line.getQuantity(), line.getUnitCostCents(), note, admin));
            stockAfter.put(product.getId(), stockQty);
        }
        log.info("Delivery recorded: lines={}, products={}, adminId={}", request.getLines().size(), stockAfter.size(), adminId);
        return toProductStockDtos(stockAfter, products);
    }

    /**
     * For every counted product writes one STOCKTAKE movement of (counted - current stock); nothing when they match.
     * The counted products are locked first, so a sale cannot slip in between reading the stock and correcting it.
     */
    @Transactional
    public List<StocktakeResultDto> recordStocktake(StocktakeCreateRequestDto request, Long adminId) {
        List<Long> productIds = request.getLines().stream().map(StocktakeLineDto::getProductId).toList();
        validateEachProductOnce(productIds);
        Map<Long, Product> products = getValidProductsForUpdateBy(productIds);
        Member admin = getValidMemberBy(adminId);
        String note = request.getNote();
        List<StocktakeResultDto> results = new ArrayList<>();
        for (StocktakeLineDto line : request.getLines()) {
            results.add(correctStock(products.get(line.getProductId()), line.getCountedQty(), note, admin));
        }
        log.info("Stocktake recorded: counted={}, corrected={}, adminId={}", results.size(),
                results.stream().filter(result -> result.getDifference() != 0).count(), adminId);
        return results;
    }

    @Transactional
    public List<ProductStockDto> recordWriteOff(WriteOffCreateRequestDto request, Long adminId) {
        Map<Long, Product> products = getValidProductsBy(request.getLines().stream().map(WriteOffLineDto::getProductId).toList());
        Member admin = getValidMemberBy(adminId);
        String note = request.getNote();
        Map<Long, Integer> stockAfter = new LinkedHashMap<>();
        for (WriteOffLineDto line : request.getLines()) {
            Product product = products.get(line.getProductId());
            int stockQty = move(StockMove.writeOff(product, line.getQuantity(), note, admin));
            stockAfter.put(product.getId(), stockQty);
        }
        log.info("Write-off recorded: lines={}, adminId={}", request.getLines().size(), adminId);
        return toProductStockDtos(stockAfter, products);
    }

    private StockMovement createMovement(StockMove stockMove) {
        StockMovement movement = new StockMovement();
        movement.setProduct(stockMove.product());
        movement.setType(stockMove.type());
        movement.setQuantityDelta(stockMove.quantityDelta());
        movement.setUnitCostCents(stockMove.unitCostCents());
        movement.setPurchase(stockMove.purchase());
        movement.setNote(stockMove.note());
        movement.setCreatedBy(stockMove.createdBy());
        movement.setCreatedAt(OffsetDateTime.now(clock));
        return movement;
    }

    // One query for all lines
    private Map<Long, Product> getValidProductsBy(List<Long> productIds) {
        return toValidProductMap(productIds, productRepository.findAllById(productIds.stream().distinct().toList()));
    }

    private Map<Long, Product> getValidProductsForUpdateBy(List<Long> productIds) {
        return toValidProductMap(productIds, productRepository.findAllForUpdate(productIds));
    }

    private static Map<Long, Product> toValidProductMap(List<Long> productIds, List<Product> found) {
        Map<Long, Product> products = found.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != productIds.stream().distinct().count()) {
            throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
        }
        return products;
    }

    // Two counts for one product contradict each other
    private static void validateEachProductOnce(List<Long> productIds) {
        if (productIds.stream().distinct().count() != productIds.size()) {
            throw new ForbiddenException(Error.DUPLICATE_PRODUCT_IN_STOCKTAKE);
        }
    }

    // Reads the stock from the locked row, not from the loaded entity, which may be out of date
    private StocktakeResultDto correctStock(Product product, int countedQty, String note, Member admin) {
        int previousStockQty = productRepository.findStockQty(product.getId());
        int difference = countedQty - previousStockQty;
        if (difference != 0) {
            move(StockMove.stocktake(product, difference, note, admin));
        }
        StocktakeResultDto result = new StocktakeResultDto();
        result.setProductId(product.getId());
        result.setName(product.getName());
        result.setPreviousStockQty(previousStockQty);
        result.setCountedQty(countedQty);
        result.setDifference(difference);
        return result;
    }

    private Member getValidMemberBy(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private static List<ProductStockDto> toProductStockDtos(Map<Long, Integer> stockAfter, Map<Long, Product> products) {
        return stockAfter.entrySet().stream().map(entry -> {
            ProductStockDto dto = new ProductStockDto();
            dto.setProductId(entry.getKey());
            dto.setName(products.get(entry.getKey()).getName());
            dto.setStockQty(entry.getValue());
            return dto;
        }).toList();
    }
}
