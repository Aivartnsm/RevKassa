package ee.revkassa.service;

import ee.revkassa.controller.admin.report.dto.MonthQuantityDto;
import ee.revkassa.controller.admin.report.dto.ProductSalesDto;
import ee.revkassa.controller.admin.report.dto.SalesReportDto;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.BadRequestException;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.product.ProductRepository;
import ee.revkassa.persistence.purchaseline.ProductMonthSales;
import ee.revkassa.persistence.purchaseline.PurchaseLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The sales report: units, revenue, purchase cost and margin per product between two days (club time, both
 * included), undone purchases left out. Revenue uses the price of the moment of sale; cost uses the purchase price
 * stored with the sale, or the product's current one for sales made before it had one. The database sums per product
 * and month; products are named as they are now.
 */
@Service
@RequiredArgsConstructor
public class SalesReportService {

    private static final Comparator<ProductSalesDto> MOST_SOLD_FIRST = Comparator
            .comparingLong(ProductSalesDto::getQuantity).reversed()
            .thenComparing(ProductSalesDto::getName, String.CASE_INSENSITIVE_ORDER);

    private final PurchaseLineRepository purchaseLineRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    /**
     * @param from null = from the first sale
     * @param to   null = today
     */
    @Transactional(readOnly = true)
    public SalesReportDto getSalesReport(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : firstSaleDayOr(end);
        validateRange(start, end);
        Map<Long, List<ProductMonthSales>> salesByProductId = purchaseLineRepository
                .findMonthlySales(startOf(start), startOf(end.plusDays(1))).stream()
                .collect(Collectors.groupingBy(ProductMonthSales::getProductId));
        List<ProductSalesDto> products = Stream.concat(
                        soldProducts(salesByProductId).stream(),
                        unsoldActiveProducts(salesByProductId).stream())
                .toList();
        return createReport(start, end, products);
    }

    // ---- helpers ----

    // A range ending before the first sale is just its last day
    private LocalDate firstSaleDayOr(LocalDate end) {
        return purchaseLineRepository.findFirstSaleTime()
                .map(time -> time.atZoneSameInstant(ClockConfig.APP_ZONE).toLocalDate())
                .filter(firstDay -> firstDay.isBefore(end))
                .orElse(end);
    }

    private static void validateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new BadRequestException(Error.DATE_RANGE_INVALID);
        }
    }

    // A day starts at midnight club time
    private static OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(ClockConfig.APP_ZONE).toOffsetDateTime();
    }

    private List<ProductSalesDto> soldProducts(Map<Long, List<ProductMonthSales>> salesByProductId) {
        Map<Long, Product> products = productRepository.findAllById(salesByProductId.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return salesByProductId.entrySet().stream()
                .map(entry -> toProductSalesDto(products.get(entry.getKey()), entry.getValue()))
                .sorted(MOST_SOLD_FIRST)
                .toList();
    }

    // Slow sellers are worth seeing too; hidden products that sold nothing are not
    private List<ProductSalesDto> unsoldActiveProducts(Map<Long, List<ProductMonthSales>> salesByProductId) {
        return productRepository.findActiveProducts().stream()
                .filter(product -> !salesByProductId.containsKey(product.getId()))
                .map(product -> toProductSalesDto(product, List.of()))
                .sorted(Comparator.comparing(ProductSalesDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static ProductSalesDto toProductSalesDto(Product product, List<ProductMonthSales> monthlySales) {
        ProductSalesDto dto = new ProductSalesDto();
        dto.setProductId(product.getId());
        dto.setName(product.getName());
        dto.setActive(product.getActive());
        dto.setQuantity(sum(monthlySales, ProductMonthSales::getQuantity));
        dto.setRevenueCents(sum(monthlySales, ProductMonthSales::getRevenueCents));
        dto.setUnitsWithoutCost(sum(monthlySales, ProductMonthSales::getUnitsWithoutCost));
        // A cost for only part of the units would make the margin look better than it is
        if (dto.getUnitsWithoutCost() == 0) {
            dto.setCostCents(sum(monthlySales, ProductMonthSales::getCostCents));
            dto.setMarginCents(dto.getRevenueCents() - dto.getCostCents());
        }
        dto.setMonths(monthlySales.stream()
                .sorted(Comparator.comparing(ProductMonthSales::getMonth))
                .map(sales -> new MonthQuantityDto(YearMonth.parse(sales.getMonth()), sales.getQuantity()))
                .toList());
        return dto;
    }

    private static long sum(List<ProductMonthSales> monthlySales, Function<ProductMonthSales, Long> value) {
        return monthlySales.stream().map(value).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private static SalesReportDto createReport(LocalDate start, LocalDate end, List<ProductSalesDto> products) {
        SalesReportDto report = new SalesReportDto();
        report.setFrom(start);
        report.setTo(end);
        report.setMonths(monthsBetween(YearMonth.from(start), YearMonth.from(end)));
        report.setProducts(products);
        report.setTotalQuantity(products.stream().mapToLong(ProductSalesDto::getQuantity).sum());
        report.setTotalRevenueCents(products.stream().mapToLong(ProductSalesDto::getRevenueCents).sum());
        report.setTotalCostCents(products.stream().map(ProductSalesDto::getCostCents).filter(Objects::nonNull)
                .mapToLong(Long::longValue).sum());
        report.setTotalMarginCents(products.stream().map(ProductSalesDto::getMarginCents).filter(Objects::nonNull)
                .mapToLong(Long::longValue).sum());
        report.setUnitsWithoutCost(products.stream().mapToLong(ProductSalesDto::getUnitsWithoutCost).sum());
        return report;
    }

    private static List<YearMonth> monthsBetween(YearMonth first, YearMonth last) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }
}
