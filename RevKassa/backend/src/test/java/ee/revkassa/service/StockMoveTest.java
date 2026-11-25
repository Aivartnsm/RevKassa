package ee.revkassa.service;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.stockmovement.StockMovementTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockMoveTest {

    private final Product product = new Product();
    private final Member member = new Member();
    private final Purchase purchase = new Purchase();

    @Test
    void factories_giveTheRightSign() {
        assertThat(StockMove.delivery(product, 24, 110L, null, member).quantityDelta()).isEqualTo(24);
        assertThat(StockMove.writeOff(product, 2, "katki", member).quantityDelta()).isEqualTo(-2);
        assertThat(StockMove.sale(product, 3, purchase, member).quantityDelta()).isEqualTo(-3);
        assertThat(StockMove.stocktake(product, -4, null, member).quantityDelta()).isEqualTo(-4);
        assertThat(StockMove.stocktake(product, 4, null, member).quantityDelta()).isEqualTo(4);
    }

    @ParameterizedTest
    @EnumSource(StockMovementTypeEnum.class)
    void zeroChange_isRefusedForEveryType(StockMovementTypeEnum type) {
        Purchase link = type.isPurchaseRequired() ? purchase : null;

        assertThatThrownBy(() -> new StockMove(product, type, 0, null, link, null, member))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saleWithoutPurchase_isRefused() {
        assertThatThrownBy(() -> new StockMove(product, StockMovementTypeEnum.SALE, -1, null, null, null, member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a purchase");
    }

    @Test
    void deliveryWithPurchase_isRefused() {
        assertThatThrownBy(() -> new StockMove(product, StockMovementTypeEnum.DELIVERY, 5, null, purchase, null, member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have");
    }

    @Test
    void unitCostOutsideDelivery_isRefused() {
        assertThatThrownBy(() -> new StockMove(product, StockMovementTypeEnum.WRITE_OFF, -1, 100L, null, "x", member))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positiveSaleOrNegativeDelivery_isRefused() {
        assertThatThrownBy(() -> new StockMove(product, StockMovementTypeEnum.SALE, 1, null, purchase, null, member))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockMove.delivery(product, -1, null, null, member))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankNote_becomesEmpty() {
        assertThat(StockMove.writeOff(product, 1, "  katki  ", member).note()).isEqualTo("katki");
        assertThat(StockMove.delivery(product, 1, null, "  ", member).note()).isNull();
    }
}
