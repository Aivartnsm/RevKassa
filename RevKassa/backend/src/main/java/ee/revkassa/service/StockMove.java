package ee.revkassa.service;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.product.Product;
import ee.revkassa.persistence.purchase.Purchase;
import ee.revkassa.persistence.stockmovement.StockMovementTypeEnum;

import java.util.Objects;

/**
 * One stock change to write with StockService.move. Only valid combinations can be built: the type fixes the sign of
 * the change and whether a purchase is linked (sales and their reversals, nothing else). Breaking a rule is a
 * programming error (IllegalArgumentException): callers validate user input first.
 * Create it with the method for the type.
 */
public record StockMove(Product product, StockMovementTypeEnum type, int quantityDelta, Long unitCostCents,
                        Purchase purchase, String note, Member createdBy) {

    public StockMove {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdBy, "createdBy");
        if (!type.getSign().allows(quantityDelta)) {
            throw new IllegalArgumentException(type + " needs a " + type.getSign() + " quantity change, got " + quantityDelta);
        }
        if (type.isPurchaseRequired() != (purchase != null)) {
            throw new IllegalArgumentException(type + (purchase != null ? " must not have" : " needs") + " a purchase");
        }
        if (unitCostCents != null && type != StockMovementTypeEnum.DELIVERY) {
            throw new IllegalArgumentException("Only a DELIVERY has a unit cost");
        }
        note = note == null || note.isBlank() ? null : note.strip();
    }

    public static StockMove delivery(Product product, int quantity, Long unitCostCents, String note, Member admin) {
        return new StockMove(product, StockMovementTypeEnum.DELIVERY, quantity, unitCostCents, null, note, admin);
    }

    // difference = counted - stock before the count
    public static StockMove stocktake(Product product, int difference, String note, Member admin) {
        return new StockMove(product, StockMovementTypeEnum.STOCKTAKE, difference, null, null, note, admin);
    }

    public static StockMove writeOff(Product product, int quantity, String note, Member admin) {
        return new StockMove(product, StockMovementTypeEnum.WRITE_OFF, -quantity, null, null, note, admin);
    }

    public static StockMove sale(Product product, int quantity, Purchase purchase, Member member) {
        return new StockMove(product, StockMovementTypeEnum.SALE, -quantity, null, purchase, null, member);
    }
}
