package ee.revkassa.persistence.stockmovement;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Each type fixes the sign of the quantity change and whether the row belongs to a purchase. StockMove checks these
 * before anything is written.
 */
@Getter
@RequiredArgsConstructor
public enum StockMovementTypeEnum {
    DELIVERY(QuantitySignEnum.POSITIVE, false),
    SALE(QuantitySignEnum.NEGATIVE, true),
    SALE_REVERSAL(QuantitySignEnum.POSITIVE, true),
    STOCKTAKE(QuantitySignEnum.ANY, false),
    WRITE_OFF(QuantitySignEnum.NEGATIVE, false);

    private final QuantitySignEnum sign;
    private final boolean purchaseRequired;

    public enum QuantitySignEnum {
        NEGATIVE, POSITIVE, ANY;

        public boolean allows(int quantityDelta) {
            return switch (this) {
                case NEGATIVE -> quantityDelta < 0;
                case POSITIVE -> quantityDelta > 0;
                case ANY -> quantityDelta != 0;
            };
        }
    }
}
