package ee.revkassa.controller.payment.dto;

import ee.revkassa.persistence.payment.PaymentMethodEnum;
import ee.revkassa.persistence.payment.PaymentStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class PaymentDto implements Serializable {
    private Long paymentId;
    private Long amountCents;
    private PaymentMethodEnum method;
    private String reference;
    private PaymentStatusEnum status;
    private OffsetDateTime reportedAt;
    // null while PENDING
    private OffsetDateTime decidedAt;
    // The admin's note, e.g. why it was rejected
    private String decisionNote;
}
