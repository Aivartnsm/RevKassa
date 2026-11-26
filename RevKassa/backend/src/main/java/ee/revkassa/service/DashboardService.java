package ee.revkassa.service;

import ee.revkassa.controller.admin.dashboard.dto.DashboardDto;
import ee.revkassa.controller.admin.dashboard.dto.StockCheckStatusEnum;
import ee.revkassa.controller.admin.dashboard.dto.StockToCheckDto;
import ee.revkassa.infrastructure.config.ClockConfig;
import ee.revkassa.persistence.member.DebtTotal;
import ee.revkassa.persistence.member.MemberMapper;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.payment.PaymentRepository;
import ee.revkassa.persistence.payment.PaymentTotal;
import ee.revkassa.persistence.product.ProductMapper;
import ee.revkassa.persistence.product.ProductRepository;
import ee.revkassa.persistence.purchaseline.PurchaseLineMapper;
import ee.revkassa.persistence.purchaseline.PurchaseLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * The admin's overview: what needs attention (debts, payments to confirm, stock to refill or count) and the month's
 * best sellers. Every figure is computed by the database from the cached balance and stock columns.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    public static final int LARGEST_DEBTS = 5;
    public static final int TOP_PRODUCTS = 5;
    public static final int UPCOMING_EVENTS = 3;

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final PurchaseLineRepository purchaseLineRepository;
    private final PurchaseLineMapper purchaseLineMapper;
    private final EventService eventService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardDto getDashboard() {
        DashboardDto dto = new DashboardDto();
        addDebts(dto);
        addPendingPayments(dto);
        addStockToCheck(dto);
        addTopProductsOf(YearMonth.now(clock), dto);
        dto.setUpcomingEvents(eventService.getUpcomingEvents(UPCOMING_EVENTS));
        return dto;
    }

    private void addDebts(DashboardDto dto) {
        DebtTotal debts = memberRepository.findDebtTotal();
        dto.setOpenDebtCents(debts.owedCents());
        dto.setDebtorCount(debts.debtorCount());
        dto.setLargestDebts(memberMapper.toDebtorDtos(
                memberRepository.findLargestDebtors(PageRequest.of(0, LARGEST_DEBTS))));
    }

    private void addPendingPayments(DashboardDto dto) {
        PaymentTotal pending = paymentRepository.findPendingTotal();
        dto.setPendingPaymentCount(pending.count());
        dto.setPendingPaymentCents(pending.amountCents());
    }

    private void addStockToCheck(DashboardDto dto) {
        List<StockToCheckDto> stockToCheck = productMapper.toStockToCheckDtos(productRepository.findStockToCheck());
        dto.setStockToCheck(stockToCheck);
        dto.setLowStockCount(countWith(stockToCheck, StockCheckStatusEnum.LOW));
        dto.setNegativeStockCount(countWith(stockToCheck, StockCheckStatusEnum.NEGATIVE));
    }

    private static long countWith(List<StockToCheckDto> stockToCheck, StockCheckStatusEnum status) {
        return stockToCheck.stream().filter(product -> product.getStatus() == status).count();
    }

    // The month runs from midnight on the 1st to midnight on the next 1st, club time
    private void addTopProductsOf(YearMonth month, DashboardDto dto) {
        dto.setMonth(month);
        dto.setTopProducts(purchaseLineMapper.toTopProductDtos(purchaseLineRepository.findTopSellers(
                startOf(month), startOf(month.plusMonths(1)), PageRequest.of(0, TOP_PRODUCTS))));
    }

    private static OffsetDateTime startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ClockConfig.APP_ZONE).toOffsetDateTime();
    }
}
