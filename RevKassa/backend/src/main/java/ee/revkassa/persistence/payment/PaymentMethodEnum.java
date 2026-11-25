package ee.revkassa.persistence.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How the member paid. Only bank transfer for now; the column stays for a possible card terminal later.
 */
@Getter
@RequiredArgsConstructor
public enum PaymentMethodEnum {
    BANK_TRANSFER("pangaülekanne");

    // Shown in the balance history: "Makse · pangaülekanne"
    private final String description;
}
